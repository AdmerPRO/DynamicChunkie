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
    @org.spongepowered.asm.mixin.Unique private long dynamicchunkie$submitStart;

    @Inject(method="submit",at=@At("HEAD"))
    private void dynamicchunkie$sampleStart(net.minecraft.client.renderer.entity.state.EntityRenderState state,
            net.minecraft.client.renderer.state.level.CameraRenderState camera,double x,double y,double z,
            com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.SubmitNodeCollector nodes,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dynamicchunkie$submitStart=dev.dynamicchunkie.visual.EntityModelCosts.begin(state.entityType);
    }
    @Inject(method="submit",at=@At("RETURN"))
    private void dynamicchunkie$sampleEnd(net.minecraft.client.renderer.entity.state.EntityRenderState state,
            net.minecraft.client.renderer.state.level.CameraRenderState camera,double x,double y,double z,
            com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.SubmitNodeCollector nodes,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dev.dynamicchunkie.visual.EntityModelCosts.finish(state.entityType,dynamicchunkie$submitStart);
    }

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
