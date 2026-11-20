package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.DynamicChunkie;
import net.minecraft.client.renderer.entity.layers.SheepWoolUndercoatLayer;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SheepWoolUndercoatLayer.class)
abstract class MobDetailMixin {
    @Inject(method="submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/SheepRenderState;FF)V", at=@At("HEAD"), cancellable=true)
    private void dynamicchunkie$detail(PoseStack pose, SubmitNodeCollector nodes, int light, SheepRenderState state,float yaw,float pitch,CallbackInfo ci) {
        var c=DynamicChunkie.settings();
        if (c!=null && c.enabled && c.mobDetailDistance>0 && state.distanceToCameraSq>c.mobDetailDistance*c.mobDetailDistance)
            ci.cancel();
    }
}
