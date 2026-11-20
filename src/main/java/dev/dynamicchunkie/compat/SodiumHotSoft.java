package dev.dynamicchunkie.compat;
import dev.dynamicchunkie.mixin.sodium.*;
import dev.dynamicchunkie.render.SectionBackend;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
public final class SodiumHotSoft implements SectionBackend {
    @Override public boolean collect(Visitor visitor) {
        var renderer=SodiumWorldRenderer.instance();if(renderer==null)return false;
        var manager=((SodiumRendererAccessor)(Object)renderer).dynamicchunkie$getManager();if(manager==null)return false;
        var iterator=manager.getRenderLists().iterator(false);
        while(iterator.hasNext()) {
            var list=iterator.next();var region=list.getRegion();
            var geometry=((SodiumSectionCandidatesAccess)(Object)list).dynamicchunkie$geometry();
            for(int i=0;i<list.getSectionsWithGeometryCount();i++) {
                int index=Byte.toUnsignedInt(geometry[i]);
                if(!visitor.visit(region.getOriginX()+16*LocalSectionIndex.unpackX(index),
                        region.getOriginY()+16*LocalSectionIndex.unpackY(index),region.getOriginZ()+16*LocalSectionIndex.unpackZ(index)))return false;
            }
        }
        return true;
    }
    private static net.caffeinemc.mods.sodium.client.render.chunk.RenderSection section(int x,int y,int z) {
        var renderer = SodiumWorldRenderer.instance();
        if (renderer == null) return null;
        var manager = ((SodiumRendererAccessor)(Object)renderer).dynamicchunkie$getManager();
        return manager == null ? null : (net.caffeinemc.mods.sodium.client.render.chunk.RenderSection)
                ((SodiumDistanceAccess)(Object)manager).dynamicchunkie$sectionAt(x,y,z);
    }
    public static void visibilityChanged(int x,int y,int z) {
        var section=section(x,y,z);
        if (section!=null) SodiumTerrainCache.visibilityChanged(section.getRegion());
    }
    private static boolean allocated(net.caffeinemc.mods.sodium.client.render.chunk.RenderSection section,
                                     net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass pass) {
        var storage = section.getRegion().getStorage(pass);
        return storage != null && ((SodiumMeshAllocations)(Object)storage).dynamicchunkie$vertices()[section.getSectionIndex()] != null;
    }
    @Override public boolean hasBuiltMesh(int x,int y,int z) {
        var section = section(x,y,z);
        if (section == null || !section.isBuilt() || section.isInvisible()) return false;
        for (var pass : net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.ALL)
            if (allocated(section,pass)) return true;
        return false;
    }
    public static long opaqueMeshCost(int x,int y,int z) {
        var section = section(x,y,z);
        if (section == null || section.isDisposed() || !section.isBuilt() || section.getPendingUpdate() != 0 ||
                allocated(section,net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.TRANSLUCENT))
            return -1;
        var jobs = (SodiumMeshJobs)(Object)section;
        if (!jobs.dynamicchunkie$jobs().isEmpty() || jobs.dynamicchunkie$buildOutput() != null ||
                jobs.dynamicchunkie$sortOutput() != null) return -1;
        return section.getLastMeshResultSize();
    }
    public static boolean retire(int x,int y,int z) {
        var section = section(x,y,z);
        if (section == null || opaqueMeshCost(x,y,z) < 0) return false;
        var region = section.getRegion();
        boolean released = false;
        for (var pass : net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.ALL) {
            if (!allocated(section,pass)) continue;
            region.clearCachedBatchFor(pass);
            region.getStorage(pass).removeData(section.getSectionIndex());
            released = true;
        }
        return released;
    }
    @Override public void requestRebuild(int x,int y,int z) {
        var renderer=SodiumWorldRenderer.instance();if(renderer==null)return;
        var manager=((SodiumRendererAccessor)(Object)renderer).dynamicchunkie$getManager();
        if(manager!=null)manager.scheduleRebuild(x,y,z,false);
    }
}
