package dev.dynamicchunkie.render;

import dev.dynamicchunkie.RenderCache;
import net.fabricmc.loader.api.FabricLoader;

/** Each flag describes an operation validated against the installed renderer. */
public record RendererCapabilities(boolean visibleSectionTraversal, boolean rebuildDeferral,
                                   boolean coldMeshRetirement, boolean completedGpuTelemetry) {
    private static final class Installed {
        static final RendererCapabilities VALUE = detect(RenderCache.sodiumPresent(), RenderCache.sodiumSupported(),
                FabricLoader.getInstance().isModLoaded("iris"));
    }
    public static RendererCapabilities current() { return Installed.VALUE; }
    static RendererCapabilities detect(boolean sodium, boolean testedSodium, boolean iris) {
        boolean visibility = !iris && (!sodium || testedSodium);
        return new RendererCapabilities(visibility, visibility, false, true);
    }
}
