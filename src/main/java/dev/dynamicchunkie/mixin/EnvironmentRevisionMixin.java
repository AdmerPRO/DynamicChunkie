package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.EnvironmentRevision;
import dev.dynamicchunkie.render.RenderChanges;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class EnvironmentRevisionMixin implements EnvironmentRevision {
    @Unique private final RenderChanges dynamicchunkie$changes = new RenderChanges();
    @Override public RenderChanges dynamicchunkie$changes() { return dynamicchunkie$changes; }

    @Inject(method="sendBlockUpdated",at=@At("RETURN"))
    private void dynamicchunkie$updated(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState oldState,
                                       net.minecraft.world.level.block.state.BlockState newState,int flags,CallbackInfo ci) {
        dynamicchunkie$changes.block(pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4);
    }
    @Inject(method="setBlocksDirty",at=@At("RETURN"))
    private void dynamicchunkie$dirty(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState oldState,
                                       net.minecraft.world.level.block.state.BlockState newState,CallbackInfo ci) {
        dynamicchunkie$changes.block(pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4);
    }
}
