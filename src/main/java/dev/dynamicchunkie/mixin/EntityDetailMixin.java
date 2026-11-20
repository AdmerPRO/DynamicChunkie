package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.DynamicChunkie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
abstract class EntityDetailMixin {
    @Inject(method="extractShadow", at=@At("HEAD"), cancellable=true)
    private void dynamicchunkie$shadow(EntityRenderState state, Minecraft client, Level level, CallbackInfo ci) {
        var c=DynamicChunkie.settings();
        if (c!=null && c.enabled && c.entityShadowDistance>0 && state.distanceToCameraSq>c.entityShadowDistance*c.entityShadowDistance) {
            state.shadowPieces.clear();
            state.shadowRadius=0;
            ci.cancel();
        }
    }
    @Inject(method="extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
            at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/entity/state/EntityRenderState;distanceToCameraSq:D",
            opcode=org.objectweb.asm.Opcodes.PUTFIELD,shift=At.Shift.AFTER), cancellable=true)
    private void dynamicchunkie$name(Entity entity, EntityRenderState state,float tick,double name,double score,CallbackInfo ci) {
        var c=DynamicChunkie.settings();
        if (c==null || !c.enabled || c.entityNameDistance==0 || entity instanceof Player ||
                entity.isCurrentlyGlowing() || entity.isCustomNameVisible()) return;
        if (state.distanceToCameraSq>c.entityNameDistance*c.entityNameDistance) {
            state.nameTag=null;
            state.nameTagAttachment=null;
            state.scoreText=null;
            ci.cancel();
        }
    }
}
