package dev.dynamicchunkie.mixin;
import dev.dynamicchunkie.visual.VisualProfiler;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelExtractor.class)
abstract class ProfilerExtractionMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @Inject(method="extract",at=@At("RETURN"))
    private void dynamicchunkie$counts(CallbackInfo ci) {
        VisualProfiler.extracted(levelRenderState.entityRenderStates.size(),levelRenderState.blockEntityRenderStates.size());
    }
}
