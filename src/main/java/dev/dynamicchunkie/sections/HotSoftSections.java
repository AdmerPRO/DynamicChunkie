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
    private static HotSoftGrid current=new HotSoftGrid(),working=new HotSoftGrid();
    private static final LongLinkedOpenHashSet deferred=new LongLinkedOpenHashSet();
    private static Object world;
    private static long revision,deadline,captureNanos,deferredTotal,replayedTotal,fallbackFrames;
    private static int visited,peakDeferred;
    private static boolean ready,replaying;
    private HotSoftSections() {}
    private static boolean enabled() {
        var c=DynamicChunkie.settings();
        return c!=null && c.enabled && c.enableHotSoftSections && dev.dynamicchunkie.render.RendererCapabilities.current().visibleSectionTraversal();
    }
    public static void capture() {
        var client=Minecraft.getInstance();
        if(world!=client.level) {
            world=client.level;deferred.clear();current.clear();working.clear();
            if(ready)TerrainCulling.invalidateDrawBatches();ready=false;
        }
        if(!enabled() || client.level==null || client.player==null) {
            if(ready){ready=false;current.clear();working.clear();TerrainCulling.invalidateDrawBatches();}
            captureNanos=0;replay(true);return;
        }
        long start=System.nanoTime();deadline=start+DynamicChunkie.settings().hotSoftScanBudgetMicros*1000L;
        working.clear();visited=0;
        boolean complete=SectionBackends.current().collect(COLLECTOR);
        var camera=client.gameRenderer.mainCamera().position();
        working.add((int)Math.floor(camera.x/16),(int)Math.floor(camera.y/16),(int)Math.floor(camera.z/16));
        captureNanos=System.nanoTime()-start;
        if(!complete) {
            fallbackFrames++;if(ready)TerrainCulling.invalidateDrawBatches();ready=false;replay(true);return;
        }
        boolean changed=!ready || !current.sameHot(working);
        var old=current;current=working;working=old;ready=true;
        revision=((EnvironmentRevision)client.level).dynamicchunkie$revision();
        if(changed)TerrainCulling.invalidateDrawBatches();
        replay(false);
    }
    private static boolean consider(int x,int y,int z) {
        if(++visited>32768 || (visited%64==0 && System.nanoTime()>deadline))return false;
        if(inView(x,y,z) && !TerrainCulling.hide(x,y,z))working.add(x>>4,y>>4,z>>4);
        return true;
    }
    private static boolean inView(int x,int y,int z) {
        var frustum=Minecraft.getInstance().gameRenderer.mainCamera().getCullFrustum();
        if(frustum==null)return true;
        int r=((FrustumBoundsAccess)(Object)frustum).dynamicchunkie$bounds(x-2,y-2,z-2,x+18,y+18,z+18);
        return r==-1 || r==-2;
    }
    public static boolean active(){return ready && enabled() && Minecraft.getInstance().level==world;}
    public static boolean drawAllowed(int x,int y,int z) {
        var client=Minecraft.getInstance();
        if(!active() || ((EnvironmentRevision)client.level).dynamicchunkie$revision()!=revision)return true;
        boolean hot=current.isHot(HotSoftGrid.key(x>>4,y>>4,z>>4));
        if(!hot)TerrainCulling.noteExternalSkip(x,y,z);
        return hot;
    }
    public static boolean defer(int x,int y,int z,boolean important) {
        var client=Minecraft.getInstance();
        if(!client.isSameThread())return false;
        if(important)deferred.remove(HotSoftGrid.key(x,y,z));
        if(replaying || important || !active() || deferred.size()>=Math.min(8192, DynamicChunkie.settings().coldMetadataLimitKiB * 16) ||
                current.state(x,y,z)!=HotSoftGrid.State.COLD || inView(x*16,y*16,z*16))return false;
        boolean built=SectionBackends.current().hasBuiltMesh(x,y,z);
        if(!built)return false;
        if(deferred.add(HotSoftGrid.key(x,y,z)))deferredTotal++;
        peakDeferred=Math.max(peakDeferred,deferred.size());
        return true;
    }
    private static void replay(boolean all) {
        var client=Minecraft.getInstance();if(client.level==null)return;
        if(deferred.isEmpty())return;
        int budget=128;
        // Replay visible sections first. Then rotate a bounded slice of cold entries,
        // so a long cold prefix cannot starve newly warm sections.
        if(!all) {
            var hot=current.hotIterator();int inspectedHot=0;
            while(hot.hasNext() && budget>0 && inspectedHot++<2048) {
                long key=hot.nextLong();
                if(deferred.remove(key)){rebuild(key);budget--;}
            }
        }
        int examined=Math.min(deferred.size(),256);
        while(examined-->0 && !deferred.isEmpty() && budget>0) {
            long key=deferred.removeFirstLong();
            int x=HotSoftGrid.x(key),y=HotSoftGrid.y(key),z=HotSoftGrid.z(key);
            if(!client.level.getChunkSource().hasChunk(x,z))continue;
            if(!all && current.state(x,y,z)==HotSoftGrid.State.COLD && !inView(x*16,y*16,z*16)){deferred.add(key);continue;}
            rebuild(key);budget--;
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
    public static void unload(net.minecraft.client.multiplayer.ClientLevel level, int x, int z) {
        if (level != world || deferred.isEmpty()) return;
        for (int y = level.getMinSectionY(); y <= level.getMaxSectionY(); y++)
            deferred.remove(HotSoftGrid.key(x, y, z));
    }
    public static void clear(){world=null;ready=false;current=new HotSoftGrid();working=new HotSoftGrid();deferred.clear();deferred.trim();peakDeferred=0;captureNanos=0;}
    public static int hotCount(){return ready?current.hotCount():0;}
    public static int logicalSoftCount(){return ready?current.softCount():0;}
    public static HotSoftGrid.State state(int x,int y,int z){return current.state(x,y,z);}
    public static boolean isDeferred(int x,int y,int z){return deferred.contains(HotSoftGrid.key(x,y,z));}
    public static int pending(){return deferred.size();}
    public static long deferredTotal(){return deferredTotal;}
    public static long replayedTotal(){return replayedTotal;}
    public static long fallbackFrames(){return fallbackFrames;}
    public static long captureNanos(){return captureNanos;}
    public static long chargedBytes(){return current.chargedBytes()+working.chargedBytes()+128+peakDeferred*64L;}
}
