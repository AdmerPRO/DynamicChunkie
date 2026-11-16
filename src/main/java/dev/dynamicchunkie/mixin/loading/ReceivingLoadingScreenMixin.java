package dev.dynamicchunkie.mixin.loading;
import dev.dynamicchunkie.loading.LoadingPresentation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ReceivingLevelScreen.class)
abstract class ReceivingLoadingScreenMixin extends Screen {
    protected ReceivingLoadingScreenMixin(){super(Component.empty());}
    @Inject(method="renderBackground",at=@At("HEAD"),cancellable=true)
    private void dynamicchunkie$background(GuiGraphics g,int x,int y,float d,CallbackInfo ci){
        if(LoadingPresentation.simple())ci.cancel();
    }
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void dynamicchunkie$bar(GuiGraphics g,int mx,int my,float d,CallbackInfo ci){
        if(!LoadingPresentation.simple())return;
        int x=width/2-100,y=height/2-38,offset=(int)((System.nanoTime()/10_000_000L)%140);
        g.fill(x,y,x+200,y+2,0x40202020);
        g.fill(x+offset,y,x+offset+60,y+2,0xBFFFFFFF);
        ci.cancel();
    }
}
