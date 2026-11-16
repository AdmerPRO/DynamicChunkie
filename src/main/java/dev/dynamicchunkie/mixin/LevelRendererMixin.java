package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.CachedViewArea;
import dev.dynamicchunkie.RenderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin implements dev.dynamicchunkie.RenderStorageAccess {
    @Override public Object dynamicchunkie$viewArea() { return viewArea; }
    @Shadow @Final private Minecraft minecraft;
    @Shadow private ViewArea viewArea;
    @Shadow private int lastViewDistance;
    @Shadow @Final private SectionOcclusionGraph sectionOcclusionGraph;
    @Shadow public abstract void allChanged();

    @ModifyArg(method = "allChanged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ViewArea;<init>(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;Lnet/minecraft/world/level/Level;ILnet/minecraft/client/renderer/LevelRenderer;)V"), index = 2)
    private int dynamicchunkie$reserve(int actual) { return RenderCache.allocationRadius(actual); }

    @Inject(method = "allChanged", at = @At("RETURN"))
    private void dynamicchunkie$radius(CallbackInfo ci) {
        if ((Object)viewArea instanceof CachedViewArea cached && cached.dynamicchunkie$isManaged())
            cached.dynamicchunkie$setVisibleRadius(minecraft.options.getEffectiveRenderDistance());
    }

    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;allChanged()V"))
    private void dynamicchunkie$reuse(LevelRenderer renderer) {
        int radius = minecraft.options.getEffectiveRenderDistance();
        if (RenderCache.sodiumEnabled()) { lastViewDistance = radius; return; }
        if (RenderCache.enabled() && (Object)viewArea instanceof CachedViewArea cached &&
                cached.dynamicchunkie$isManaged() && radius <= cached.dynamicchunkie$capacity()) {
            cached.dynamicchunkie$setVisibleRadius(radius);
            lastViewDistance = radius;
            sectionOcclusionGraph.invalidate();
            RenderCache.recordReuse();
        } else allChanged();
    }
}
