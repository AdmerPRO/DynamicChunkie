package dev.dynamicchunkie.mixin.sodium;
import dev.dynamicchunkie.sections.HotSoftSections;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(value=RenderSectionManager.class,remap=false)
abstract class SodiumHotSoftUpdateMixin {
    @Inject(method="scheduleRebuild",at=@At("HEAD"),cancellable=true)
    private void dynamicchunkie$defer(int x,int y,int z,boolean important,CallbackInfo ci) {
        if(HotSoftSections.defer(x,y,z,important))ci.cancel();
    }
}
