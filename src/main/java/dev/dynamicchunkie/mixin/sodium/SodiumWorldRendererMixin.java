package dev.dynamicchunkie.mixin.sodium;

import dev.dynamicchunkie.RenderCache;
import dev.dynamicchunkie.compat.SodiumDistanceAccess;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
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
    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void dynamicchunkie$keepRenderer(CallbackInfo ci) {
        if (!RenderCache.sodiumEnabled() || renderSectionManager == null) return;
        int radius = client.options.getEffectiveRenderDistance();
        if (renderDistance == radius) return;
        ((SodiumDistanceAccess)(Object)renderSectionManager).dynamicchunkie$setDistance(radius);
        renderDistance = radius; // Satisfy Sodium's RD comparison without calling reload().
        RenderCache.recordReuse();
    }
}
