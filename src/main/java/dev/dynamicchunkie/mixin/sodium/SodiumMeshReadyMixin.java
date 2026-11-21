package dev.dynamicchunkie.mixin.sodium;

import dev.dynamicchunkie.sections.HotSoftSections;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo @Mixin(value=RenderSection.class,remap=false)
abstract class SodiumMeshReadyMixin implements dev.dynamicchunkie.compat.SodiumCostState {
    @org.spongepowered.asm.mixin.Unique private long dynamicchunkie$cost=-2;

    public long dynamicchunkie$geometryCost() {
        if (dynamicchunkie$cost==-2)
            dynamicchunkie$cost=dev.dynamicchunkie.compat.SodiumHotSoft.measure((RenderSection)(Object)this);
        return dynamicchunkie$cost;
    }

    public void dynamicchunkie$invalidateCost() {dynamicchunkie$cost=-2;}

    @Inject(method="setInfo",at=@At("RETURN"))
    private void dynamicchunkie$changed(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> ci) {
        dynamicchunkie$invalidateCost();
    }

    @Inject(method="setLastMeshResultSize",at=@At("RETURN"))
    private void dynamicchunkie$published(long size,CallbackInfo ci) {
        dynamicchunkie$invalidateCost();
        var section=(RenderSection)(Object)this;
        HotSoftSections.meshCompleted(section.getChunkX(),section.getChunkY(),section.getChunkZ());
    }
}
