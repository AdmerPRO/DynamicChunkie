package dev.dynamicchunkie.mixin.loading;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dynamicchunkie.loading.LoadingPresentation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LoadingOverlay.class)
abstract class LoadingOverlayMixin {
    @WrapMethod(method="render")
    private void dynamicchunkie$barOnly(GuiGraphics graphics,int mouseX,int mouseY,float delta,Operation<Void> original) {
        if (!LoadingPresentation.simple()) {original.call(graphics,mouseX,mouseY,delta);return;}
        int y=(int)(graphics.guiHeight()*.8325);
        graphics.enableScissor(0,y-6,graphics.guiWidth(),y+6);
        try {original.call(graphics,mouseX,mouseY,delta);}
        finally {graphics.disableScissor();}
    }
    @WrapOperation(method="render",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"))
    private void dynamicchunkie$noBrandBackground(GuiGraphics graphics,RenderType type,int a,int b,int c,int d,int color,Operation<Void> original) {
        if (!LoadingPresentation.simple()) original.call(graphics,type,a,b,c,d,color);
    }
    @ModifyVariable(method="drawProgressBar",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float dynamicchunkie$translucentBar(float opacity) {
        return LoadingPresentation.simple()?opacity*.75f:opacity;
    }
    // Older GL render paths clear directly; no-op when that call is absent on this version.
    @WrapOperation(method="render",at=@At(value="INVOKE",
            target="Lcom/mojang/blaze3d/platform/GlStateManager;_clear(I)V"),require=0)
    private void dynamicchunkie$noClear(int mask,Operation<Void> original) {
        if (!LoadingPresentation.simple()) original.call(mask);
    }
}
