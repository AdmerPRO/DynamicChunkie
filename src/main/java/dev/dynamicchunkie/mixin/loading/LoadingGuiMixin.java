package dev.dynamicchunkie.mixin.loading;

import dev.dynamicchunkie.loading.LoadingPresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class LoadingGuiMixin {
    @Inject(method="getOverlay",at=@At("RETURN"),cancellable=true)
    private void dynamicchunkie$inputOnly(CallbackInfoReturnable<Overlay> cir) {
        if (LoadingPresentation.inInput()) cir.setReturnValue(LoadingPresentation.inputOverlay(cir.getReturnValue()));
    }
}
