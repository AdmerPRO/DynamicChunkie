package dev.dynamicchunkie.mixin.sodium;

import dev.dynamicchunkie.RenderCache;
import dev.dynamicchunkie.compat.SodiumDistanceAccess;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = SodiumWorldRenderer.class, remap = false)
abstract class SodiumWorldRendererMixin {
    @Shadow @Final private Minecraft client;
    @Shadow private int renderDistance;
    @Shadow private RenderSectionManager renderSectionManager;
    @Shadow private UniformBufferManager uniformBufferManager;

    @ModifyArg(method = "initRenderer", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;<init>(Lnet/minecraft/client/multiplayer/ClientLevel;I)V"), index = 1)
    private int dynamicchunkie$reserveTimes(int actual) {
        return RenderCache.sodiumAllocationRadius(actual);
    }

    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void dynamicchunkie$keepRenderer(CallbackInfo ci) {
        if (!RenderCache.sodiumEnabled() || renderSectionManager == null || uniformBufferManager == null) return;
        int radius = client.options.getEffectiveRenderDistance();
        if (renderDistance == radius) return;
        ((SodiumDistanceAccess)(Object)renderSectionManager).dynamicchunkie$setDistance(radius);
        renderDistance = radius; // Satisfy Sodium's RD comparison without calling reload().
        RenderCache.recordReuse();
    }
}
