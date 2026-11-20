package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.DynamicChunkie;
import dev.dynamicchunkie.diagnostics.EntityRejections;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"))
    private void dynamicchunkie$beginDecision(CallbackInfoReturnable<Boolean> result) {
        EntityRejections.clearHint();
    }

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void dynamicchunkie$occluded(Entity entity, Frustum frustum, double x, double y, double z,
            float partialTick, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            EntityRejections.record(EntityRejections.nativeReason(entity.getId()), entity.getId());
            return;
        }
        var reason = dev.dynamicchunkie.visual.EntityVisualPolicy.rejection(entity, x, y, z);
        if (reason == null && DynamicChunkie.entityCulling() != null &&
                DynamicChunkie.entityCulling().shouldCull(entity, x, y, z)) reason = EntityRejections.Reason.OCCLUSION;
        if (reason != null) {
            EntityRejections.record(reason, entity.getId());
            cir.setReturnValue(false);
        }
    }
}
