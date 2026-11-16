package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.EnvironmentRevision;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class EnvironmentRevisionMixin implements EnvironmentRevision {
    @Unique private long dynamicchunkie$revision;
    @Override public long dynamicchunkie$revision() {return dynamicchunkie$revision;}
    @Inject(method={"sendBlockUpdated","setBlocksDirty","onChunkLoaded","unload"},at=@At("RETURN"))
    private void dynamicchunkie$changed(CallbackInfo ci) {dynamicchunkie$revision++;}
}
