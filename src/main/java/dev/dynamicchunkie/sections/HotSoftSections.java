package dev.dynamicchunkie.sections;
import dev.dynamicchunkie.*;
import dev.dynamicchunkie.mixin.FrustumBoundsAccess;
import dev.dynamicchunkie.terrain.TerrainCulling;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.client.Minecraft;
import dev.dynamicchunkie.render.SectionBackend;
import dev.dynamicchunkie.render.SectionBackends;
/** Main-thread working set. Retains cold meshes and defers only safe offscreen rebuilds. */
public final class HotSoftSections {
    private static final SectionBackend.Visitor COLLECTOR=HotSoftSections::consider;
    private static HotSoftGrid current=new HotSoftGrid();
    private static final it.unimi.dsi.fastutil.longs.LongOpenHashSet working = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private static int peakWorking;
    private static final SectionView view = new SectionView();
    public static void verifyView() {
        if (filtering() && !view.matches(Minecraft.getInstance())) {
            ready=false;
            TerrainCulling.invalidateDrawBatches();
        }
    }
    private static final ColdRegions coldRegions = new ColdRegions();
    private static final it.unimi.dsi.fastutil.longs.LongOpenHashSet nearby = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private static final LongLinkedOpenHashSet visibleReplay = new LongLinkedOpenHashSet();
    private static final LongLinkedOpenHashSet deferred=new LongLinkedOpenHashSet();
    private static final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap coldSince =
            new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private static long lastRetirementTick = Long.MIN_VALUE;
    private static long evictions;
    private static Object world;
    private static long revision,deadline,captureNanos,deferredTotal,replayedTotal,fallbackFrames;
    private static int visited,peakDeferred;
    private static boolean ready,replaying,drawFilter;
    private HotSoftSections() {}
    private static boolean enabled() {
        var c=DynamicChunkie.settings();
        return c!=null && c.enabled && c.enableHotSoftSections && dev.dynamicchunkie.render.RendererCapabilities.current().visibleSectionTraversal();
    }
    public static void capture() {
        var client=Minecraft.getInstance();
        if(world!=client.level) {
            world=client.level;deferred.clear();coldRegions.clear();visibleReplay.clear();nearby.clear();coldSince.clear();lastRetirementTick=Long.MIN_VALUE;current.clear();working.clear();
            if(ready)TerrainCulling.invalidateDrawBatches();ready=false;
        }
        if(!enabled() || client.level==null || client.player==null) {
            if(ready){ready=false;current.clear();working.clear();TerrainCulling.invalidateDrawBatches();}
            captureNanos=0;replay(true);return;
        }
        boolean previousFilter=drawFilter;
        drawFilter=DynamicChunkie.settings().filterHotSectionDraws;
        if (!drawFilter) {
            if (previousFilter) {
                current.clear();
                TerrainCulling.invalidateDrawBatches();
            }
            ready=DynamicChunkie.settings().deferColdSectionRebuilds;
            captureNanos=0;
            replay(!ready);
            return;
        }
        long start=System.nanoTime();deadline=start+DynamicChunkie.settings().hotSoftScanBudgetMicros*1000L;
        working.clear();visibleReplay.clear();visited=0;
        boolean complete=SectionBackends.current().collect(COLLECTOR);
        var camera=client.gameRenderer.mainCamera().position();
        working.add(HotSoftGrid.key((int)Math.floor(camera.x/16),(int)Math.floor(camera.y/16),(int)Math.floor(camera.z/16)));
        peakWorking=Math.max(peakWorking,working.size());
        captureNanos=System.nanoTime()-start;
        if(!complete) {
            fallbackFrames++;if(ready)TerrainCulling.invalidateDrawBatches();ready=false;replay(true);return;
        }
        boolean resetBatches=!ready || !previousFilter;
        java.util.function.LongConsumer notify=null;
        if (!resetBatches && RenderCache.sodiumSupported()) notify=HotSoftSections::sectionChanged;
        current.update(working,notify);
        view.capture(client);
        ready=true;
        revision=((EnvironmentRevision)client.level).dynamicchunkie$revision();
        if(resetBatches)TerrainCulling.invalidateDrawBatches();
        replay(!DynamicChunkie.settings().deferColdSectionRebuilds);
    }
    private static void sectionChanged(long key) {
        dev.dynamicchunkie.compat.SodiumHotSoft.visibilityChanged(HotSoftGrid.x(key),HotSoftGrid.y(key),HotSoftGrid.z(key));
    }
    private static boolean consider(int x,int y,int z) {
        if(++visited>32768 || (visited%64==0 && System.nanoTime()>deadline))return false;
        if(inView(x,y,z) && !TerrainCulling.hide(x,y,z)) {
            long key=HotSoftGrid.key(x>>4,y>>4,z>>4);
            working.add(key);
            if (deferred.contains(key)) visibleReplay.add(key);
        }
        return true;
    }
    private static boolean inView(int x,int y,int z) {
        return intersectsView(x,y,z,2);
    }
    private static boolean intersectsView(int x,int y,int z,int margin) {
        var frustum=Minecraft.getInstance().gameRenderer.mainCamera().getCullFrustum();
        if(frustum==null)return true;
        int r=((FrustumBoundsAccess)(Object)frustum).dynamicchunkie$bounds(x-margin,y-margin,z-margin,x+16+margin,y+16+margin,z+16+margin);
        return r==-1 || r==-2;
    }
    public static boolean active(){return ready && enabled() && Minecraft.getInstance().level==world;}
    public static boolean filtering(){return active() && drawFilter && DynamicChunkie.settings().filterHotSectionDraws;}
    private static boolean protectedView(int x,int y,int z) {return intersectsView(x*16,y*16,z*16,drawFilter?2:18);}
    public static boolean drawAllowed(int x,int y,int z) {
        var client=Minecraft.getInstance();
        if(!filtering() || ((EnvironmentRevision)client.level).dynamicchunkie$revision()!=revision)return true;
        boolean hot=current.isHot(HotSoftGrid.key(x>>4,y>>4,z>>4));
        if(!hot)TerrainCulling.noteExternalSkip(x,y,z);
        return hot;
    }
    public static boolean defer(int x,int y,int z,boolean important) {
        var client=Minecraft.getInstance();
        if(!client.isSameThread())return false;
        if(important)forget(HotSoftGrid.key(x,y,z));
        if(replaying || important || !active() || !DynamicChunkie.settings().deferColdSectionRebuilds || deferred.size()>=Math.min(8192, DynamicChunkie.settings().coldMetadataLimitKiB * 8) ||
                current.state(x,y,z)!=HotSoftGrid.State.COLD || protectedView(x,y,z))return false;
        boolean built=SectionBackends.current().hasBuiltMesh(x,y,z);
        if(!built)return false;
        long key = HotSoftGrid.key(x,y,z);
        if(deferred.add(key)) {
            coldRegions.add(key);
            deferredTotal++;
            if (DynamicChunkie.settings().enableColdGeometryEviction) coldSince.put(key,System.nanoTime());
        }
        peakDeferred=Math.max(peakDeferred,deferred.size());
        return true;
    }
    private static void replay(boolean all) {
        var client=Minecraft.getInstance();if(client.level==null)return;
        if(deferred.isEmpty())return;
        int budget=128;
        if(!all) {
            var camera=client.gameRenderer.mainCamera().position();
            int x=(int)Math.floor(camera.x/16), y=(int)Math.floor(camera.y/16), z=(int)Math.floor(camera.z/16);
            while (!visibleReplay.isEmpty() && budget>0) {
                long key=visibleReplay.removeFirstLong();
                if(forget(key)){rebuild(key);budget--;}
            }
            coldRegions.near(x,y,z,nearby);
            var nearbyKeys=nearby.iterator();
            while (nearbyKeys.hasNext() && budget>0) {
                long key=nearbyKeys.nextLong();
                int sx=HotSoftGrid.x(key),sy=HotSoftGrid.y(key),sz=HotSoftGrid.z(key);
                if ((current.state(sx,sy,sz)!=HotSoftGrid.State.COLD || protectedView(sx,sy,sz)) && forget(key)) {
                    rebuild(key);
                    budget--;
                }
            }
        }
        int examined=Math.min(deferred.size(),256);
        while(examined-->0 && !deferred.isEmpty() && budget>0) {
            long key=deferred.removeFirstLong();
            int x=HotSoftGrid.x(key),y=HotSoftGrid.y(key),z=HotSoftGrid.z(key);
            if(!client.level.getChunkSource().hasChunk(x,z)){coldRegions.remove(key);coldSince.remove(key);continue;}
            if(!all && (DynamicChunkie.settings().enableColdGeometryEviction || coldSince.get(key)!=Long.MAX_VALUE) && current.state(x,y,z)==HotSoftGrid.State.COLD && !protectedView(x,y,z)){deferred.add(key);continue;}
            coldRegions.remove(key);coldSince.remove(key);rebuild(key);budget--;
        }
    }
    @SuppressWarnings("PMD.UnusedAssignment") // Native rebuild hooks synchronously re-enter defer and read replaying.
    private static void rebuild(long key) {
        var client=Minecraft.getInstance();
        int x=HotSoftGrid.x(key),y=HotSoftGrid.y(key),z=HotSoftGrid.z(key);
        if(!client.level.getChunkSource().hasChunk(x,z))return;
        replaying=true;
        try {
            SectionBackends.current().requestRebuild(x,y,z);
            replayedTotal++;
        }finally{replaying=false;}
    }
    private static boolean forget(long key) {
        coldSince.remove(key);
        coldRegions.remove(key);
        return deferred.remove(key);
    }

    /** Runs inside the pinned Sodium region maintenance phase. Sections and visibility data stay registered. */
    public static void retireColdMesh() {
        var cfg = DynamicChunkie.settings();
        if (cfg == null || !cfg.enableColdGeometryEviction || coldSince.isEmpty() || !active() ||
                !dev.dynamicchunkie.render.RendererCapabilities.current().coldMeshRetirement()) return;
        var client = Minecraft.getInstance();
        if (!client.isSameThread() || !client.levelRenderer.hasRenderedAllSections() ||
                ((EnvironmentRevision)client.level).dynamicchunkie$revision()!=revision) return;
        long tick = client.level.getGameTime();
        if (tick == lastRetirementTick) return;
        lastRetirementTick = tick;
        var camera = client.gameRenderer.mainCamera().position();
        long now = System.nanoTime(), best = 0;
        double priority = -1;
        int examined = 0;
        var iterator = deferred.iterator();
        while (iterator.hasNext() && examined++ < 32) {
            long key = iterator.nextLong(), since = coldSince.get(key);
            if (since == 0 || since == Long.MAX_VALUE || now-since < cfg.coldGeometryGraceMs*1_000_000L) continue;
            int x=HotSoftGrid.x(key), y=HotSoftGrid.y(key), z=HotSoftGrid.z(key);
            if (current.state(x,y,z)!=HotSoftGrid.State.COLD || protectedView(x,y,z)) continue;
            long meshBytes = dev.dynamicchunkie.compat.SodiumHotSoft.opaqueMeshCost(x,y,z);
            if (meshBytes < 0) continue;
            double rank = camera.distanceToSqr(x*16+8,y*16+8,z*16+8) / (1.0+meshBytes);
            if (rank > priority) {priority=rank;best=key;}
        }
        if (priority >= 0 && dev.dynamicchunkie.compat.SodiumHotSoft.retire(HotSoftGrid.x(best),HotSoftGrid.y(best),HotSoftGrid.z(best))) {
            coldSince.put(best,Long.MAX_VALUE);
            evictions++;
        }
    }
    public static long evictions() { return evictions; }

    public static void unload(net.minecraft.client.multiplayer.ClientLevel level, int x, int z) {
        if (level != world || deferred.isEmpty()) return;
        for (int y = level.getMinSectionY(); y <= level.getMaxSectionY(); y++)
            forget(HotSoftGrid.key(x, y, z));
    }
    public static void clear(){world=null;ready=false;current=new HotSoftGrid();working.clear();working.trim();peakWorking=0;deferred.clear();deferred.trim();coldRegions.clear();visibleReplay.clear();visibleReplay.trim();nearby.clear();nearby.trim();coldSince.clear();coldSince.trim();peakDeferred=0;captureNanos=0;}
    public static int hotCount(){return ready?current.hotCount():0;}
    public static int logicalSoftCount(){return ready?current.softCount():0;}
    public static HotSoftGrid.State state(int x,int y,int z){return current.state(x,y,z);}
    public static boolean isDeferred(int x,int y,int z){return deferred.contains(HotSoftGrid.key(x,y,z));}
    public static int pending(){return deferred.size();}
    public static long deferredTotal(){return deferredTotal;}
    public static long replayedTotal(){return replayedTotal;}
    public static long fallbackFrames(){return fallbackFrames;}
    public static long captureNanos(){return captureNanos;}
    public static long chargedBytes(){return current.chargedBytes()+peakWorking*32L+128+peakDeferred*128L;}
}
