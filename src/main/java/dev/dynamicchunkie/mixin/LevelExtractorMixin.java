package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.CachedViewArea;
import dev.dynamicchunkie.RenderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private LevelRenderer levelRenderer;
    @Shadow private int lastViewDistance;
    @Shadow private SectionUpdateTracker sectionUpdateTracker;

    @ModifyArg(method = "allChanged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/SectionUpdateTracker;<init>(Lnet/minecraft/world/level/LevelHeightAccessor;I)V"), index = 1)
    private int dynamicchunkie$reserveUpdateTracking(int actual) {
        return RenderCache.allocationRadius(actual);
    }

    // Only the RD comparison in extract is intercepted. F3+A, resource reloads, video
    // settings and world changes still call allChanged and rebuild geometry normally.
    @Redirect(method = "extract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;allChanged()V"))
    private void dynamicchunkie$reuseGeometry(LevelExtractor extractor) {
        int radius = minecraft.options.getEffectiveRenderDistance();
        if (RenderCache.sodiumEnabled()) {
            // Sodium owns the geometry; its setupTerrain hook updates distance in-place.
            lastViewDistance = radius;
            return;
        }
        if (RenderCache.enabled() && sectionUpdateTracker != null &&
                (Object)levelRenderer.viewArea() instanceof CachedViewArea cached &&
                cached.dynamicchunkie$isManaged() && radius <= cached.dynamicchunkie$capacity()) {
            cached.dynamicchunkie$setVisibleRadius(radius);
            lastViewDistance = radius;
            // Vanilla builds a replacement visibility graph in the background and
            // keeps the old one until it is ready. Do not reset meshes or compile queues.
            levelRenderer.sectionOcclusionGraph().invalidate();
            RenderCache.recordReuse();
        } else extractor.allChanged();
    }
}
