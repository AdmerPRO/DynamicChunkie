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
    @Override public boolean hasBuiltMesh(int x,int y,int z) {
        var renderer=SodiumWorldRenderer.instance();if(renderer==null)return false;
        var manager=((SodiumRendererAccessor)(Object)renderer).dynamicchunkie$getManager();if(manager==null)return false;
        var section=(net.caffeinemc.mods.sodium.client.render.chunk.RenderSection)((SodiumDistanceAccess)(Object)manager).dynamicchunkie$sectionAt(x,y,z);
        return section!=null && section.isBuilt() && !section.isInvisible();
    }
    @Override public void requestRebuild(int x,int y,int z) {
        var renderer=SodiumWorldRenderer.instance();if(renderer==null)return;
        var manager=((SodiumRendererAccessor)(Object)renderer).dynamicchunkie$getManager();
        if(manager!=null)manager.scheduleRebuild(x,y,z,false);
    }
}
