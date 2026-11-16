package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.DynamicChunkie;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class PerformanceFrameMixin {
    @Inject(method="renderFrame",at=@At("RETURN"))
    private void dynamicchunkie$frame(CallbackInfo ci) {DynamicChunkie.onFrame();}
}
