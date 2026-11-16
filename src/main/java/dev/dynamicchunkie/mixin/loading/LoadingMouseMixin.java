package dev.dynamicchunkie.mixin.loading;

import dev.dynamicchunkie.loading.LoadingPresentation;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class LoadingMouseMixin {
    @Inject(method={"onPress","onScroll","onMove","turnPlayer","grabMouse","handleAccumulatedMovement"},at=@At("HEAD"))
    private void dynamicchunkie$start(CallbackInfo ci) {LoadingPresentation.beginInput();}
    @Inject(method={"onPress","onScroll","onMove","turnPlayer","grabMouse","handleAccumulatedMovement"},at=@At("RETURN"))
    private void dynamicchunkie$end(CallbackInfo ci) {LoadingPresentation.endInput();}
}
