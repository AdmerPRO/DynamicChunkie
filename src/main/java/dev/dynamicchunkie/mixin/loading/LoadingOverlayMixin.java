package dev.dynamicchunkie.mixin.loading;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dynamicchunkie.loading.LoadingPresentation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LoadingOverlay.class)
abstract class LoadingOverlayMixin {
    @WrapMethod(method="extractRenderState")
    private void dynamicchunkie$barOnly(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta,Operation<Void> original) {
        if (!LoadingPresentation.simple()) {original.call(graphics,mouseX,mouseY,delta);return;}
        int y=(int)(graphics.guiHeight()*.8325);
        graphics.enableScissor(0,y-6,graphics.guiWidth(),y+6);
        try {original.call(graphics,mouseX,mouseY,delta);}
        finally {graphics.disableScissor();}
    }
    @WrapOperation(method="extractRenderState",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void dynamicchunkie$noBrandBackground(GuiGraphicsExtractor graphics,int a,int b,int c,int d,int color,Operation<Void> original) {
        if (!LoadingPresentation.simple()) original.call(graphics,a,b,c,d,color);
    }
    @ModifyVariable(method="extractProgressBar",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float dynamicchunkie$translucentBar(float opacity) {
        return LoadingPresentation.simple()?opacity*.75f:opacity;
    }
    // Older GL render paths clear directly; no-op when that call is absent on this version.
    @WrapOperation(method="extractRenderState",at=@At(value="INVOKE",
            target="Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"),require=0)
    private void dynamicchunkie$noClear(int mask,boolean check,Operation<Void> original) {
        if (!LoadingPresentation.simple()) original.call(mask,check);
    }
}
