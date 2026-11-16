package dev.dynamicchunkie.mixin.loading;

import dev.dynamicchunkie.loading.LoadingPresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class LoadingGameplayMixin {
    @Unique private boolean dynamicchunkie$handledKeys;
    @Invoker("handleKeybinds") public abstract void dynamicchunkie$handleKeys();
    @Inject(method="tick",at=@At("HEAD"))
    private void dynamicchunkie$startTick(CallbackInfo ci) {dynamicchunkie$handledKeys=false;}
    @Inject(method="handleKeybinds",at=@At("HEAD"))
    private void dynamicchunkie$inputStart(CallbackInfo ci) {
        dynamicchunkie$handledKeys=true;LoadingPresentation.beginInput();
    }
    @Inject(method="handleKeybinds",at=@At("RETURN"))
    private void dynamicchunkie$inputEnd(CallbackInfo ci) {LoadingPresentation.endInput();}
    @Inject(method="tick",at=@At("RETURN"))
    private void dynamicchunkie$unblockedGameplay(CallbackInfo ci) {
        Minecraft client=(Minecraft)(Object)this;
        if (!dynamicchunkie$handledKeys && LoadingPresentation.readyForInput() &&
                client.gui.screen()==null && client.gui.overlay() instanceof LoadingOverlay) {
            dynamicchunkie$handleKeys();
        }
    }
}
