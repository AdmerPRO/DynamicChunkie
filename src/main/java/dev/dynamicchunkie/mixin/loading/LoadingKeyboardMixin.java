package dev.dynamicchunkie.mixin.loading;

import dev.dynamicchunkie.loading.LoadingPresentation;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class LoadingKeyboardMixin {
    @Inject(method={"keyPress","charTyped"},at=@At("HEAD"))
    private void dynamicchunkie$start(CallbackInfo ci) {LoadingPresentation.beginInput();}
    @Inject(method={"keyPress","charTyped"},at=@At("RETURN"))
    private void dynamicchunkie$end(CallbackInfo ci) {LoadingPresentation.endInput();}
}
