package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.diagnostics.EntityRejections;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererDiagnosticsMixin {
    // In 26.3 the first return rejects the entity's native distance/visibility predicate.
    @Inject(method="shouldRender", at=@At(value="RETURN", ordinal=0))
    private void dynamicchunkie$distance(Entity entity, Frustum frustum, double x, double y, double z,
                                         float partialTick, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ()) EntityRejections.nativeHint(entity.getId(), EntityRejections.Reason.NATIVE_PREDICATE);
    }

    // The last two returns are reached only after testing the entity and any leash bounds.
    @Inject(method="shouldRender", at={@At(value="RETURN", ordinal=3), @At(value="RETURN", ordinal=4)})
    private void dynamicchunkie$frustum(Entity entity, Frustum frustum, double x, double y, double z,
                                        float partialTick, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ()) EntityRejections.nativeHint(entity.getId(), EntityRejections.Reason.NATIVE_FRUSTUM);
    }
}
