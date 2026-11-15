package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.CachedViewArea;
import dev.dynamicchunkie.RenderCache;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ViewArea.class)
abstract class ViewAreaMixin implements CachedViewArea {
    @Unique private int dynamicchunkie$capacity;
    @Unique private boolean dynamicchunkie$managed;
    // Vanilla's asynchronous graph traversal must not read Minecraft options.
    @Unique private volatile int dynamicchunkie$visibleRadius;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dynamicchunkie$rememberCapacity(SectionRenderDispatcher dispatcher, int minY, int maxY,
            int minSectionY, int maxSectionY, int radius, SectionOcclusionGraph graph, CallbackInfo ci) {
        dynamicchunkie$capacity = radius;
        dynamicchunkie$visibleRadius = radius;
        dynamicchunkie$managed = RenderCache.enabled();
    }

    @Inject(method = "getViewDistance", at = @At("HEAD"), cancellable = true)
    private void dynamicchunkie$visibleRadius(CallbackInfoReturnable<Integer> cir) {
        if (dynamicchunkie$managed) cir.setReturnValue(dynamicchunkie$visibleRadius);
    }

    @Override public int dynamicchunkie$capacity() { return dynamicchunkie$capacity; }
    @Override public boolean dynamicchunkie$isManaged() { return dynamicchunkie$managed; }
    @Override public void dynamicchunkie$setVisibleRadius(int radius) {
        dynamicchunkie$visibleRadius = Math.max(2, Math.min(dynamicchunkie$capacity, radius));
    }
}
