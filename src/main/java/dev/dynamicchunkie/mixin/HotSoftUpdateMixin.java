package dev.dynamicchunkie.mixin;
import dev.dynamicchunkie.sections.HotSoftSections;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelExtractor.class)
abstract class HotSoftUpdateMixin {
    @Inject(method="setSectionDirty(IIIZ)V",at=@At("HEAD"),cancellable=true)
    private void dynamicchunkie$defer(int x,int y,int z,boolean important,CallbackInfo ci) {
        if(HotSoftSections.defer(x,y,z,important))ci.cancel();
    }
}
