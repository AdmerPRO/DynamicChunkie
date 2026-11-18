package dev.dynamicchunkie.mixin;
import dev.dynamicchunkie.DynamicChunkie;
import dev.dynamicchunkie.visual.EntityVisualPolicy;
import net.minecraft.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Options.class)
abstract class CloudQualityMixin {
    @Inject(method="getCloudStatus",at=@At("RETURN"),cancellable=true)
    private void dynamicchunkie$quality(CallbackInfoReturnable<CloudStatus> cir) {
        var c=DynamicChunkie.configOrNull();
        if(c==null || !c.enabled || !c.enableSmartClouds)return;
        double scale=EntityVisualPolicy.scale();
        cir.setReturnValue(dev.dynamicchunkie.visual.VisualBudget.clouds(cir.getReturnValue(),scale));
    }
}
