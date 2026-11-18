package dev.dynamicchunkie;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Reserves vanilla section slots once; geometry stays owned by Minecraft.
 * No chunk data or off-world GPU buffers are kept in a separate cache.
 */
public final class RenderCache {
    private static DynamicChunkieConfig config;
    private static boolean sodium;
    private static boolean sodiumCompatible;
    private static long preservedChanges;

    private RenderCache() {}
    public static void configure(DynamicChunkieConfig value) {
        config = value;
        sodium = FabricLoader.getInstance().isModLoaded("sodium");
        sodiumCompatible = dev.dynamicchunkie.mixin.CompatibilityMixinPlugin.supportedSodium();
        if (sodium && !sodiumCompatible) DynamicChunkie.LOG.warn("Unsupported Sodium version: automatic RD changes are suspended to avoid renderer reload flashes");
    }
    public static boolean enabled() { return config != null && !sodium && config.enabled && config.preserveRenderCache; }
    public static boolean sodiumEnabled() {
        return config != null && sodiumCompatible && config.enabled && config.preserveRenderCache;
    }
    public static boolean sodiumSupported() {return sodiumCompatible;}
    public static boolean sodiumPresent() {return sodium;}
    public static boolean canChangeDistance() {
        return !sodium || sodiumEnabled();
    }
    public static String status() {
        return sodiumEnabled() ? "Sodium (reuse)" : enabled() ? "Vanilla (reuse)" :
                sodium ? "Sodium: auto RD paused" : "Off";
    }
    public static int sodiumAllocationRadius(int actual) {
        // Reserve only a small timing table, not terrain/chunk data. Cover all vanilla
        // distances so enabling the mod or raising a limit in Mod Menu needs no reset.
        return sodiumCompatible ? Math.max(actual, 32) : actual;
    }
    public static int allocationRadius(int actual) {
        if (!enabled()) return actual;
        int height=24;
        var level=net.minecraft.client.Minecraft.getInstance().level;
        if (level!=null) height=level.getSectionsCount();
        return config.enableMemoryManagement ?
                dev.dynamicchunkie.adaptive.MemoryBudget.reservationRadius(actual,config.maxRenderDistance,height,config.cacheMemoryLimitMb) :
                Math.max(actual,config.maxRenderDistance);
    }
    public static void recordReuse() { preservedChanges++; }
    public static long preservedChanges() { return preservedChanges; }
}
