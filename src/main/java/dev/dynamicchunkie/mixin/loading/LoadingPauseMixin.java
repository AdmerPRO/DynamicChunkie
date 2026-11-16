package dev.dynamicchunkie.mixin.loading;

import dev.dynamicchunkie.loading.LoadingPresentation;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LoadingOverlay.class)
abstract class LoadingPauseMixin {
    @Inject(method="isPauseScreen",at=@At("RETURN"),cancellable=true)
    private void dynamicchunkie$playWhileReloading(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof LoadingOverlay && LoadingPresentation.readyForInput()) cir.setReturnValue(false);
    }
}
