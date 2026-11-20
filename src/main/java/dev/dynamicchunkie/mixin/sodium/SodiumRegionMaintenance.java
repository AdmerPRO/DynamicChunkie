package dev.dynamicchunkie.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value=RenderRegionManager.class,remap=false)
abstract class SodiumRegionMaintenance {
    @Inject(method="update",at=@At("HEAD"))
    private void dynamicchunkie$retireColdMesh(CallbackInfo ci) {
        dev.dynamicchunkie.sections.HotSoftSections.retireColdMesh();
    }
}
