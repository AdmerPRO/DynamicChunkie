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

    @Inject(method={"sendBlockUpdated","setBlocksDirty"},at=@At("RETURN"))
    private void dynamicchunkie$blocks(CallbackInfo ci) { dynamicchunkie$changes.record(RenderChanges.Kind.BLOCK); }

    @Inject(method="onChunkLoaded",at=@At("RETURN"))
    private void dynamicchunkie$loaded(CallbackInfo ci) { dynamicchunkie$changes.record(RenderChanges.Kind.CHUNK_LOAD); }

    @Inject(method="unload",at=@At("RETURN"))
    private void dynamicchunkie$unloaded(CallbackInfo ci) { dynamicchunkie$changes.record(RenderChanges.Kind.CHUNK_UNLOAD); }
}
