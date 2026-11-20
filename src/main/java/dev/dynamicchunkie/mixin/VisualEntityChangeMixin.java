package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.visual.EntityVisualPolicy;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Motion and synchronized names/flags can change the nearest eligible set inside one section. */
@Mixin(Entity.class)
abstract class VisualEntityChangeMixin {
    @Inject(method="setPosRaw", at=@At(value="FIELD", target="Lnet/minecraft/world/entity/Entity;position:Lnet/minecraft/world/phys/Vec3;",
            opcode=org.objectweb.asm.Opcodes.PUTFIELD))
    private void dynamicchunkie$moved(CallbackInfo ci) {
        dynamicchunkie$changed();
    }

    @Inject(method="onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at=@At("RETURN"))
    private void dynamicchunkie$selectionChanged(CallbackInfo ci) {
        dynamicchunkie$changed();
    }

    @org.spongepowered.asm.mixin.Unique
    private void dynamicchunkie$changed() {
        var entity=(Entity)(Object)this;
        if (entity.level() instanceof net.minecraft.client.multiplayer.ClientLevel)
            EntityVisualPolicy.entityChanged(entity);
    }
}
