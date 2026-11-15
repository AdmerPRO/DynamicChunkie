package dev.dynamicchunkie;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Reserves vanilla section slots once; geometry stays owned by Minecraft.
 * No chunk data or off-world GPU buffers are kept in a separate cache.
 */
public final class RenderCache {
    private static DynamicChunkieConfig config;
    private static boolean compatible;
    private static long preservedChanges;

    private RenderCache() {}
    public static void configure(DynamicChunkieConfig value) {
        config = value;
        compatible = !FabricLoader.getInstance().isModLoaded("sodium");
        if (!compatible) DynamicChunkie.LOG.info("Sodium detected: leaving render storage management to Sodium");
    }
    public static boolean enabled() { return config != null && compatible && config.enabled && config.preserveRenderCache; }
    public static int allocationRadius(int actual) {
        return enabled() ? Math.max(actual, config.maxRenderDistance) : actual;
    }
    public static void recordReuse() { preservedChanges++; }
    public static long preservedChanges() { return preservedChanges; }
}
