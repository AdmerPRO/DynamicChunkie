package dev.dynamicchunkie.mixin.loading;

import dev.dynamicchunkie.loading.LoadingPresentation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
abstract class WorldLoadingScreenMixin extends Screen {
    @Shadow @Final private StoringChunkProgressListener progressListener;
    @Unique private long dynamicchunkie$lastNarration;
    protected WorldLoadingScreenMixin() {super(Component.empty());}

    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void dynamicchunkie$progressOnly(GuiGraphics graphics,int mouseX,int mouseY,float delta,CallbackInfo ci) {
        if (!LoadingPresentation.simple()) return;
        long now=System.nanoTime();
        if (now-dynamicchunkie$lastNarration>2_000_000_000L) {
            triggerImmediateNarration(true);dynamicchunkie$lastNarration=now;
        }
        int y=height/2-38, x=width/2-100;
        graphics.fill(x,y,x+200,y+2,0x40202020);
        graphics.fill(x,y,x+Math.max(0,Math.min(200,progressListener.getProgress()*2)),y+2,0xBFFFFFFF);
        ci.cancel();
    }
}
