package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.CachedViewArea;
import dev.dynamicchunkie.RenderCache;
import net.minecraft.client.Camera;
import net.minecraft.client.Options;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow private ViewArea viewArea;

    @ModifyArg(method = "invalidateCompiledGeometry", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ViewArea;<init>(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;IIIIILnet/minecraft/client/renderer/SectionOcclusionGraph;)V"), index = 5)
    private int dynamicchunkie$reserveCapacity(int actual) {
        return RenderCache.allocationRadius(actual);
    }

    @Inject(method = "invalidateCompiledGeometry", at = @At("RETURN"))
    private void dynamicchunkie$setInitialRadius(ClientLevel level, Options options, Camera camera,
            BlockColors colors, CallbackInfo ci) {
        if ((Object)viewArea instanceof CachedViewArea cached && cached.dynamicchunkie$isManaged())
            cached.dynamicchunkie$setVisibleRadius(options.getEffectiveRenderDistance());
    }
}
