package dev.dynamicchunkie.terrain;

import dev.dynamicchunkie.runtime.WorkCoordinator;
import dev.dynamicchunkie.runtime.AnalysisScheduler;

import dev.dynamicchunkie.*;
import it.unimi.dsi.fastutil.longs.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

/** Palette-only discovery on the client tick; render hooks only consult verified immutable boxes. */
public final class TerrainCulling {
    private record Box(int x,int y,int z) {}
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
    private static final LongOpenHashSet coveredGroups=new LongOpenHashSet();
    private static long seenGeometry=-1, localRevision;
    private static int emptyFrames;
    private static long nextProbe;
    private static final LongOpenHashSet skipped=new LongOpenHashSet();
    private static final LongOpenHashSet candidateSections=new LongOpenHashSet();
    private static final LongOpenHashSet countedRegions=new LongOpenHashSet();
    private static int candidates,lastCandidates;
    private static Object world;
    private static long observedOcclusionSkips;
    private static long revision=-1,changedAt,generation,spentNanos;
    private static boolean incomplete,wasActive,meshesReady;
    private static int sx,sy,sz,cursor,checks,hookCalls,lastSkipped,lastHooks;
    private static double cx=Double.NaN,cy,cz;
    private static volatile List<Box> boxes=List.of();
    private static final Box[][] octants=new Box[8][128];
    private static final int[] sizes=new int[8];
    private static List<Box> indexedBoxes=List.of();
    private static double indexX,indexY,indexZ;
    private static float probeYaw,probePitch;
    private static void indexOccluders() {
        if (indexedBoxes==boxes && indexX==cx && indexY==cy && indexZ==cz) return;
        Arrays.fill(sizes,0);
        for (Box box:boxes) for (int octant=0;octant<8;octant++) {
            if (((octant&1)==0 ? box.x>cx : box.x+16<cx) ||
                    ((octant&2)==0 ? box.y>cy : box.y+16<cy) ||
                    ((octant&4)==0 ? box.z>cz : box.z+16<cz)) continue;
            octants[octant][sizes[octant]++]=box;
        }
        indexedBoxes=boxes;indexX=cx;indexY=cy;indexZ=cz;
    }
    private TerrainCulling() {}
    private static int[][] offsets() {
        var values=new ArrayList<int[]>();
        for(int x=-8;x<=8;x++)for(int y=-8;y<=8;y++)for(int z=-8;z<=8;z++)values.add(new int[]{x,y,z});
        values.sort(Comparator.comparingInt(a->a[0]*a[0]+a[1]*a[1]+a[2]*a[2]));
        return values.toArray(int[][]::new);
    }
    public static void invalidate() {
        var settings = DynamicChunkie.settings();
        boolean measure = settings != null && (settings.diagnosticsEnabled || settings.enableJfrEvents);
        long start = measure ? System.nanoTime() : 0;
        int discarded = decisions.size();
        emptyFrames=0;nextProbe=0;seenGeometry=-1;boxes=List.of();discovered.clear();decisions.clear();coveredGroups.clear();cursor=0;revision=-1;generation++;changedAt=System.nanoTime();
        if (measure) WorkCoordinator.complete(AnalysisScheduler.Module.TERRAIN_INVALIDATION, start, discarded);
    }
    public static void release() {
        invalidate();
        world = null;
        decisions.trim();
        skipped.clear(); skipped.trim();
        candidateSections.clear(); candidateSections.trim();
        countedRegions.clear(); countedRegions.trim();
    }

    private static long localRevision(Minecraft client,int x,int y,int z) {
        var changes=((EnvironmentRevision)client.level).dynamicchunkie$changes();
        if (seenGeometry!=changes.geometry() || client.level!=world || x!=sx || y!=sy || z!=sz) {
            localRevision=changes.region(x-8,y-8,z-8,x+8,y+8,z+8,false);
            seenGeometry=changes.geometry();
        }
        return localRevision;
    }
    public static void tick(Minecraft client) {
        var c=DynamicChunkie.settings();
        if(c==null || !c.enabled || !c.enableTerrainSectionCulling || !dev.dynamicchunkie.render.RendererCapabilities.current().visibleSectionTraversal() || client.level==null || client.player==null || client.player.isSpectator()) {
            if(world!=null)invalidate();world=null;return;
        }
        var camera=client.gameRenderer.mainCamera().position();
        int x=(int)Math.floor(camera.x/16),y=(int)Math.floor(camera.y/16),z=(int)Math.floor(camera.z/16);
        long current=localRevision(client,x,y,z);
        if(world!=client.level || revision!=current || sx!=x || sy!=y || sz!=z) {
            invalidate();world=client.level;revision=current;sx=x;sy=y;sz=z;
        }
        // Never use newly placed blocks as occluders before their native meshes are ready.
        if(!client.levelRenderer.hasRenderedAllSections()) {if(!boxes.isEmpty()) {boxes=List.of();decisions.clear();coveredGroups.clear();generation++;}return;}
        if(System.nanoTime()-changedAt<1_000_000_000L)return;
        if (cursor >= OFFSETS.length || discovered.size() >= 128 ||
                !WorkCoordinator.admit(AnalysisScheduler.Module.TERRAIN_DISCOVERY, 50_000_000L, false)) return;
        long started = System.nanoTime();
        long deadline=WorkCoordinator.deadline(1_000_000L);
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
        WorkCoordinator.complete(AnalysisScheduler.Module.TERRAIN_DISCOVERY, started, observedOcclusionSkips);
        observedOcclusionSkips=0;
        if(boxes.size()!=discovered.size()) {boxes=List.copyOf(discovered);decisions.clear();coveredGroups.clear();generation++;}
    }
    public static void prepareForDraw() {
        var client=Minecraft.getInstance();
        if(client.level==null)return;
        var changes = ((EnvironmentRevision)client.level).dynamicchunkie$changes();
        boolean ready = client.levelRenderer.hasRenderedAllSections();
        if (ready != meshesReady) {
            changes.record(dev.dynamicchunkie.render.RenderChanges.Kind.MESH_READINESS);
            meshesReady = ready;
        }
        var camera=client.gameRenderer.mainCamera().position();
        if(camera.x!=cx || camera.y!=cy || camera.z!=cz) {
            emptyFrames=0;nextProbe=0;
            changes.record(dev.dynamicchunkie.render.RenderChanges.Kind.CAMERA);
            decisions.clear();coveredGroups.clear();cx=camera.x;cy=camera.y;cz=camera.z;generation++;
        }
        if((world!=client.level || localRevision(client,sx,sy,sz)!=revision || !ready) && !boxes.isEmpty()) {
            boxes=List.of();decisions.clear();coveredGroups.clear();generation++;
        }
        boolean enabled=active();
        if(wasActive!=enabled || incomplete)generation++;
        boolean updateBatches=enabled || wasActive;
        wasActive=enabled;incomplete=false;
        if(updateBatches && RenderCache.sodiumSupported())dev.dynamicchunkie.compat.SodiumTerrainCache.prepare(generation);
    }
    public static void invalidateDrawBatches() {
        generation++;
        if(RenderCache.sodiumSupported())dev.dynamicchunkie.compat.SodiumTerrainCache.prepare(generation);
    }
    public static void noteExternalSkip(int x,int y,int z){skipped.add(SectionPos.asLong(x>>4,y>>4,z>>4));}
    public static void noteCachedSkip(long key){if(active() || dev.dynamicchunkie.sections.HotSoftSections.active())skipped.add(key);}
    public static void beginFrame() {
        skipped.clear();candidateSections.clear();countedRegions.clear();candidates=0;checks=0;hookCalls=0;spentNanos=0;
    }
    public static void endFrame() {
        if (!skipped.isEmpty()) {emptyFrames=0;nextProbe=0;}
        else if (checks>0 && ++emptyFrames>=30) nextProbe=System.nanoTime()+250_000_000L;
        lastSkipped=skipped.size();lastHooks=hookCalls;lastCandidates=candidates;}
    public static boolean metricsEnabled() {
        var c=DynamicChunkie.settings();return dev.dynamicchunkie.benchmark.FrameCapture.recording() || c!=null && (c.profilerOverlay || c.enabled && (c.enableTerrainSectionCulling || c.enableHotSoftSections));
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
        var c=DynamicChunkie.settings();
        return c!=null && c.enabled && c.enableTerrainSectionCulling && !boxes.isEmpty() &&
                dev.dynamicchunkie.render.RendererCapabilities.current().visibleSectionTraversal() &&
                (Minecraft.getInstance().player==null || !Minecraft.getInstance().player.isSpectator());
    }
    public static boolean hide(int x,int y,int z) {return occluded(x,y,z,true);}
    public static boolean occluded(int x,int y,int z,boolean countDraw) {
        var client=Minecraft.getInstance();var c=DynamicChunkie.settings();
        if(!client.isSameThread() || !active() || c==null ||
                boxes.isEmpty() || client.level==null || client.level!=world ||
                localRevision(client,sx,sy,sz)!=revision)return false;
        Vec3 camera=client.gameRenderer.mainCamera().position();
        if(camera.x!=cx || camera.y!=cy || camera.z!=cz) {
            decisions.clear();coveredGroups.clear();cx=camera.x;cy=camera.y;cz=camera.z;generation++;
        }
        var view=client.gameRenderer.mainCamera();
        if (view.yRot()!=probeYaw || view.xRot()!=probePitch) {
            emptyFrames=0;nextProbe=0;probeYaw=view.yRot();probePitch=view.xRot();
        }
        long key=SectionPos.asLong(x>>4,y>>4,z>>4);
        byte cached=decisions.get(key);
        if (cached==0 && emptyFrames>=30 && System.nanoTime()<nextProbe) {incomplete=true;return false;}
        if(cached==0 && decisions.size()<8192 && (checks>=c.terrainChecksPerFrame || spentNanos>=1_000_000L))incomplete=true;
        if(cached==0 && decisions.size()<8192 && checks<c.terrainChecksPerFrame && spentNanos<1_000_000L && !boxes.isEmpty()) {
            long started=System.nanoTime();checks++;
            int gx=x&~31, gy=y&~31, gz=z&~31;
            long group=SectionPos.asLong(gx>>5,gy>>5,gz>>5);
            boolean hidden=coveredGroups.contains(group);
            indexOccluders();
            int octant=(x+8<cx?0:1)|(y+8<cy?0:2)|(z+8<cz?0:4);
            if (!hidden) for(int i=0;i<sizes[octant];i++) {
                Box box=octants[octant][i];
                if (box.x+16<Math.min(cx,x-2) || box.x>Math.max(cx,x+18) ||
                        box.y+16<Math.min(cy,y-2) || box.y>Math.max(cy,y+18) ||
                        box.z+16<Math.min(cz,z-2) || box.z>Math.max(cz,z+18)) continue;
                if (!SectionShadow.covers(cx,cy,cz,box.x,box.y,box.z,x,y,z)) continue;
                hidden=true;
                if (coveredGroups.size()<1024 && SectionShadow.covers(cx,cy,cz,box.x,box.y,box.z,gx,gy,gz,32))
                    coveredGroups.add(group);
                break;
            }
            cached=(byte)(hidden?2:1);
            decisions.put(key,cached);spentNanos+=System.nanoTime()-started;
        }
        if(cached==2) {if (countDraw && skipped.add(key)) observedOcclusionSkips++;return true;}
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
