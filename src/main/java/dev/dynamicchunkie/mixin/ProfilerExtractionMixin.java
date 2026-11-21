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
    @Inject(method="applyFrustum",at=@At("RETURN"))
    private void dynamicchunkie$nativeListChanged(CallbackInfo ci) {
        dev.dynamicchunkie.sections.HotSoftSections.nativeListChanged();
    }
    @Inject(method="allChanged",at=@At("HEAD"))
    private void dynamicchunkie$reset(CallbackInfo ci) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.level instanceof dev.dynamicchunkie.EnvironmentRevision revision)
            revision.dynamicchunkie$changes().record(dev.dynamicchunkie.render.RenderChanges.Kind.RENDERER_RELOAD);
        dev.dynamicchunkie.sections.HotSoftSections.nativeListChanged();
        dev.dynamicchunkie.terrain.TerrainCulling.invalidate();
    }
    @Inject(method="extract",at=@At("RETURN"))
    private void dynamicchunkie$counts(CallbackInfo ci) {
        dev.dynamicchunkie.terrain.TerrainCulling.prepareForDraw();
        dev.dynamicchunkie.sections.HotSoftSections.capture();
        VisualProfiler.extracted(levelRenderState.entityRenderStates.size(),levelRenderState.blockEntityRenderStates.size());
    }
}
