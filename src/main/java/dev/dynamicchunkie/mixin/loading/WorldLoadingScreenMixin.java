package dev.dynamicchunkie.mixin.loading;

import dev.dynamicchunkie.loading.LoadingPresentation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
abstract class WorldLoadingScreenMixin extends Screen {
    @Shadow private LevelLoadTracker loadTracker;
    @Shadow private float smoothedProgress;
    @Unique private long dynamicchunkie$lastNarration;
    protected WorldLoadingScreenMixin() {super(Component.empty());}

    @Inject(method="extractBackground",at=@At("HEAD"),cancellable=true)
    private void dynamicchunkie$transparentBackground(GuiGraphicsExtractor graphics,int x,int y,float delta,CallbackInfo ci) {
        if (LoadingPresentation.simple()) ci.cancel();
    }
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void dynamicchunkie$progressOnly(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta,CallbackInfo ci) {
        if (!LoadingPresentation.simple()) return;
        long now=System.nanoTime();
        if (now-dynamicchunkie$lastNarration>2_000_000_000L) {
            triggerImmediateNarration(true);dynamicchunkie$lastNarration=now;
        }
        var status=loadTracker.statusView();
        int y=status==null?height/2-38:height/2-status.radius()*2-15;
        int x=width/2-100;
        graphics.fill(x,y,x+200,y+2,0x40202020);
        if (loadTracker.hasProgress()) {
            graphics.fill(x,y,x+(int)(200*Math.max(0,Math.min(1,smoothedProgress))),y+2,0xBFFFFFFF);
        } else {
            int offset=(int)((now/10_000_000L)%140);
            graphics.fill(x+offset,y,x+offset+60,y+2,0xBFFFFFFF);
        }
        ci.cancel();
    }
}
