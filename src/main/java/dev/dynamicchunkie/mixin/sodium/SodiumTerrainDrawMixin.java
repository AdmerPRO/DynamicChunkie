package dev.dynamicchunkie.mixin.sodium;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dynamicchunkie.terrain.TerrainCulling;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import org.spongepowered.asm.mixin.*;
/** Preserve Sodium's sorting and draw pipeline, filtering only geometry-section iterators. */
@Pseudo @Mixin(value=ChunkRenderList.class,remap=false)
abstract class SodiumTerrainDrawMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="reset",at=@org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void dynamicchunkie$nativeListChanged(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dev.dynamicchunkie.sections.HotSoftSections.nativeListChanged();
    }
    @WrapMethod(method="sectionsWithGeometryIterator")
    private ByteIterator dynamicchunkie$geometry(boolean reverse,Operation<ByteIterator> original) {
        ByteIterator source=original.call(reverse);if(source==null || !net.minecraft.client.Minecraft.getInstance().isSameThread())return source;
        TerrainCulling.hook();
        var region=((ChunkRenderList)(Object)this).getRegion();
        dev.dynamicchunkie.compat.SodiumTerrainCache.track(region);
        dev.dynamicchunkie.compat.SodiumTerrainCache.count(region);
        if(reverse || (!TerrainCulling.active() && !dev.dynamicchunkie.sections.HotSoftSections.filtering()))return source;
        return new ByteIterator() {
            private int next=-1;
            public boolean hasNext() {
                if(next>=0)return true;
                while(source.hasNext()) {
                    int index=source.nextByteAsInt();
                    int x=region.getOriginX()+16*net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex.unpackX(index);
                    int y=region.getOriginY()+16*net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex.unpackY(index);
                    int z=region.getOriginZ()+16*net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex.unpackZ(index);
                    if(!TerrainCulling.hide(x,y,z) && dev.dynamicchunkie.sections.HotSoftSections.drawAllowed(x,y,z)) {
                        next=index;return true;
                    }
                    dev.dynamicchunkie.compat.SodiumTerrainCache.hidden(region,x,y,z);
                }
                return false;
            }
            public int nextByteAsInt() {
                if(!hasNext())throw new java.util.NoSuchElementException();int value=next;next=-1;return value;
            }
        };
    }
}
