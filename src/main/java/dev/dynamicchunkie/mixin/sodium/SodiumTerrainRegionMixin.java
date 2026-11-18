package dev.dynamicchunkie.mixin.sodium;
import dev.dynamicchunkie.compat.SodiumTerrainCache;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.gpu.device.batch.MultiDrawBatch;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(value=RenderRegion.class,remap=false)
abstract class SodiumTerrainRegionMixin {
    @Inject(method="getCachedBatch",at=@At("RETURN"))
    private void dynamicchunkie$cached(TerrainRenderPass pass,CallbackInfoReturnable<MultiDrawBatch> cir) {
        if(cir.getReturnValue()!=null)SodiumTerrainCache.cached((RenderRegion)(Object)this);
    }
    @Inject(method={"clearAllCachedBatches","clearCachedBatchFor"},at=@At("HEAD"))
    private void dynamicchunkie$clear(CallbackInfo ci) {SodiumTerrainCache.clear((RenderRegion)(Object)this);}
}
