package dev.dynamicchunkie.terrain;

import dev.dynamicchunkie.*;
import it.unimi.dsi.fastutil.longs.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

/** Palette-only discovery on the client tick; render hooks only consult verified immutable boxes. */
public final class TerrainCulling {
    private record Box(int x,int y,int z) {}
    private static final boolean SHADERS=net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris");
    private static final int[][] OFFSETS=offsets();
    private static final List<Box> discovered=new ArrayList<>();
    private static final OpaqueCounter OPAQUE=new OpaqueCounter();
    private static final class OpaqueCounter implements net.minecraft.world.level.chunk.PalettedContainer.CountConsumer<net.minecraft.world.level.block.state.BlockState> {
        boolean solid;
        public void accept(net.minecraft.world.level.block.state.BlockState state,int count) {
            if(count>0 && (!state.isSolidRender() || !state.canOcclude()))solid=false;
        }
    }
    private static final Long2ByteLinkedOpenHashMap decisions=new Long2ByteLinkedOpenHashMap();
    private static final LongOpenHashSet skipped=new LongOpenHashSet();
    private static final LongOpenHashSet candidateSections=new LongOpenHashSet();
    private static final LongOpenHashSet countedRegions=new LongOpenHashSet();
    private static int candidates,lastCandidates;
    private static Object world;
    private static long revision=-1,changedAt,generation,spentNanos;
    private static boolean incomplete,wasActive;
    private static int sx,sy,sz,cursor,checks,hookCalls,lastSkipped,lastHooks;
    private static double cx=Double.NaN,cy,cz;
    private static volatile List<Box> boxes=List.of();
    private TerrainCulling() {}
    private static int[][] offsets() {
        var values=new ArrayList<int[]>();
        for(int x=-8;x<=8;x++)for(int y=-8;y<=8;y++)for(int z=-8;z<=8;z++)values.add(new int[]{x,y,z});
        values.sort(Comparator.comparingInt(a->a[0]*a[0]+a[1]*a[1]+a[2]*a[2]));
        return values.toArray(int[][]::new);
    }
    public static void invalidate() {
        boxes=List.of();discovered.clear();decisions.clear();cursor=0;revision=-1;generation++;changedAt=System.nanoTime();
    }
    public static void tick(Minecraft client) {
        var c=DynamicChunkie.configOrNull();
        if(c==null || !c.enabled || !c.enableTerrainSectionCulling || SHADERS || RenderCache.sodiumPresent() && !RenderCache.sodiumSupported() || client.level==null || client.player==null || client.player.isSpectator()) {
            if(world!=null)invalidate();world=null;return;
        }
        var camera=client.gameRenderer.mainCamera().position();
        int x=(int)Math.floor(camera.x/16),y=(int)Math.floor(camera.y/16),z=(int)Math.floor(camera.z/16);
        long current=((EnvironmentRevision)client.level).dynamicchunkie$revision();
        if(world!=client.level || revision!=current || sx!=x || sy!=y || sz!=z) {
            invalidate();world=client.level;revision=current;sx=x;sy=y;sz=z;
        }
        // Never use newly placed blocks as occluders before their native meshes are ready.
        if(!client.levelRenderer.hasRenderedAllSections()) {if(!boxes.isEmpty()) {boxes=List.of();decisions.clear();generation++;}return;}
        if(System.nanoTime()-changedAt<1_000_000_000L)return;
        long deadline=System.nanoTime()+1_000_000L;
        for(int n=0;n<16 && cursor<OFFSETS.length && discovered.size()<128 && System.nanoTime()<deadline;n++) {
            int[] offset=OFFSETS[cursor++];int bx=sx+offset[0],by=sy+offset[1],bz=sz+offset[2];
            if(!client.level.getChunkSource().hasChunk(bx,bz))continue;
            var chunk=client.level.getChunk(bx,bz);
            int section=chunk.getSectionIndexFromSectionY(by);
            if(section<0 || section>=chunk.getSections().length)continue;
            var data=chunk.getSections()[section];
            if(data.hasOnlyAir())continue;
            // Palettes may retain an unused AIR entry after a fill. Count actual state usage.
            OPAQUE.solid=true;data.getStates().count(OPAQUE);
            if(OPAQUE.solid)discovered.add(new Box(bx*16,by*16,bz*16));
        }
        if(boxes.size()!=discovered.size()) {boxes=List.copyOf(discovered);decisions.clear();generation++;}
    }
    public static void prepareForDraw() {
        var client=Minecraft.getInstance();
        if(client.level==null)return;
        var camera=client.gameRenderer.mainCamera().position();
        if(camera.x!=cx || camera.y!=cy || camera.z!=cz) {
            decisions.clear();cx=camera.x;cy=camera.y;cz=camera.z;generation++;
        }
        if((world!=client.level || ((EnvironmentRevision)client.level).dynamicchunkie$revision()!=revision ||
                !client.levelRenderer.hasRenderedAllSections()) && !boxes.isEmpty()) {
            boxes=List.of();decisions.clear();generation++;
        }
        boolean enabled=active();
        if(wasActive!=enabled || incomplete)generation++;
        boolean updateBatches=enabled || wasActive;
        wasActive=enabled;incomplete=false;
        if(updateBatches && RenderCache.sodiumSupported())dev.dynamicchunkie.compat.SodiumTerrainCache.prepare(generation);
    }
    public static void noteCachedSkip(long key) {if(active())skipped.add(key);}
    public static void beginFrame() {
        skipped.clear();candidateSections.clear();countedRegions.clear();candidates=0;checks=0;hookCalls=0;spentNanos=0;
    }
    public static void endFrame() {lastSkipped=skipped.size();lastHooks=hookCalls;lastCandidates=candidates;}
    public static boolean metricsEnabled() {
        var c=DynamicChunkie.configOrNull();return c!=null && (c.profilerOverlay || c.enabled && c.enableTerrainSectionCulling);
    }
    public static void candidate(int x,int y,int z) {
        if(metricsEnabled() && candidateSections.add(SectionPos.asLong(x>>4,y>>4,z>>4)))candidates++;
    }
    public static void region(long key,int size) {
        if(metricsEnabled() && countedRegions.add(key))candidates+=size;
    }
    public static int candidates() {return lastCandidates;}
    public static void hook() {hookCalls++;}
    public static boolean active() {
        var c=DynamicChunkie.configOrNull();
        return c!=null && c.enabled && c.enableTerrainSectionCulling && !boxes.isEmpty() &&
                !SHADERS &&
                (Minecraft.getInstance().player==null || !Minecraft.getInstance().player.isSpectator());
    }
    public static boolean hide(int x,int y,int z) {
        var client=Minecraft.getInstance();var c=DynamicChunkie.configOrNull();
        if(!client.isSameThread() || !active() || c==null ||
                boxes.isEmpty() || client.level==null || client.level!=world ||
                ((EnvironmentRevision)client.level).dynamicchunkie$revision()!=revision)return false;
        Vec3 camera=client.gameRenderer.mainCamera().position();
        if(camera.x!=cx || camera.y!=cy || camera.z!=cz) {
            decisions.clear();cx=camera.x;cy=camera.y;cz=camera.z;generation++;
        }
        long key=SectionPos.asLong(x>>4,y>>4,z>>4);
        byte cached=decisions.get(key);
        if(cached==0 && decisions.size()<8192 && (checks>=c.terrainChecksPerFrame || spentNanos>=1_000_000L))incomplete=true;
        if(cached==0 && decisions.size()<8192 && checks<c.terrainChecksPerFrame && spentNanos<1_000_000L && !boxes.isEmpty()) {
            long started=System.nanoTime();checks++;boolean hidden=false;
            for(Box box:boxes)if(SectionShadow.covers(cx,cy,cz,box.x,box.y,box.z,x,y,z)) {hidden=true;break;}
            cached=(byte)(hidden?2:1);
            decisions.put(key,cached);spentNanos+=System.nanoTime()-started;
        }
        if(cached==2) {skipped.add(key);return true;}
        return false;
    }
    public static int skipped() {return lastSkipped;}
    public static int hooks() {return lastHooks;}
    public static String status() {
        var client=Minecraft.getInstance();
        return "scan="+cursor+" boxes="+boxes.size()+" revision="+revision+"/"+
                (client.level==null?-1:((EnvironmentRevision)client.level).dynamicchunkie$revision())+
                " quietMs="+(System.nanoTime()-changedAt)/1_000_000+
                " ready="+(client.level!=null && client.levelRenderer.hasRenderedAllSections());
    }
    public static int occluders() {return boxes.size();}
    public static long chargedBytes() {return decisions.size()*48L+boxes.size()*48L+(RenderCache.sodiumSupported()?dev.dynamicchunkie.compat.SodiumTerrainCache.chargedBytes():0);}
}
