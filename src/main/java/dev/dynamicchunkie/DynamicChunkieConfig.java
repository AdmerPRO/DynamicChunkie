package dev.dynamicchunkie;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.io.IOException;

public final class DynamicChunkieConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("dynamicchunkie.json"); }
    public boolean enabled = true, enableAltitudeFactor = true, enableSkyDetection = true,
            enableElytraOptimization = true, debugMode = false, preserveRenderCache = true;
    public int minRenderDistance = 2, maxRenderDistance = 32, defaultRenderDistance = -1;
    public int tunnelDistance = 2, smallCaveDistance = 4, largeCaveDistance = 10,
            undergroundOpenDistance = 12, surfaceDistance = 16, mountainDistance = 16, highAltitudeDistance = 16;
    public int analysisIntervalMs = 750, transitionIntervalMs = 1000, deadZoneChunks = 1, targetStabilityMs = 1500;
    public double smoothingFactor = 0.35;
    // Persist before applying any dynamic value, so a crash cannot lose the user's setting.
    public boolean restorePending = false;

    public static DynamicChunkieConfig load() {
        Path PATH = path();
        try {
            DynamicChunkieConfig c = Files.exists(PATH) ? GSON.fromJson(Files.readString(PATH), DynamicChunkieConfig.class) : new DynamicChunkieConfig();
            if (c == null) throw new IOException("Empty configuration");
            c.validate();
            return c;
        } catch (IOException | RuntimeException e) {
            DynamicChunkie.LOG.warn("Cannot read configuration; using defaults without overwriting the file", e);
            return new DynamicChunkieConfig();
        }
    }
    public void validate() {
        minRenderDistance = clamp(minRenderDistance, 2, 32);
        maxRenderDistance = clamp(maxRenderDistance, minRenderDistance, 32);
        analysisIntervalMs = clamp(analysisIntervalMs, 250, 10000);
        targetStabilityMs = clamp(targetStabilityMs, 0, 10000);
        transitionIntervalMs = clamp(transitionIntervalMs, 500, 10000);
        deadZoneChunks = clamp(deadZoneChunks, 0, 4);
        smoothingFactor = Double.isFinite(smoothingFactor) ? Math.max(0.05, Math.min(1, smoothingFactor)) : 0.35;
        tunnelDistance = bound(tunnelDistance); smallCaveDistance = bound(smallCaveDistance);
        largeCaveDistance = bound(largeCaveDistance); undergroundOpenDistance = bound(undergroundOpenDistance); surfaceDistance = bound(surfaceDistance);
        mountainDistance = bound(mountainDistance); highAltitudeDistance = bound(highAltitudeDistance);
    }
    public DynamicChunkieConfig copy() {
        var result = new DynamicChunkieConfig();
        result.copySettingsFrom(this);
        result.defaultRenderDistance = defaultRenderDistance;
        result.restorePending = restorePending;
        return result;
    }
    public void copySettingsFrom(DynamicChunkieConfig other) {
        enabled = other.enabled;
        enableAltitudeFactor = other.enableAltitudeFactor;
        enableSkyDetection = other.enableSkyDetection;
        enableElytraOptimization = other.enableElytraOptimization;
        debugMode = other.debugMode;
        preserveRenderCache = other.preserveRenderCache;
        minRenderDistance = other.minRenderDistance;
        maxRenderDistance = other.maxRenderDistance;
        tunnelDistance = other.tunnelDistance;
        smallCaveDistance = other.smallCaveDistance;
        largeCaveDistance = other.largeCaveDistance;
        undergroundOpenDistance = other.undergroundOpenDistance;
        surfaceDistance = other.surfaceDistance;
        mountainDistance = other.mountainDistance;
        highAltitudeDistance = other.highAltitudeDistance;
        analysisIntervalMs = other.analysisIntervalMs;
        transitionIntervalMs = other.transitionIntervalMs;
        deadZoneChunks = other.deadZoneChunks;
        targetStabilityMs = other.targetStabilityMs;
        smoothingFactor = other.smoothingFactor;
    }
    public int bound(int n) { return clamp(n, minRenderDistance, maxRenderDistance); }
    static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
    public boolean save() {
        Path PATH = path();
        try {
            Files.createDirectories(PATH.getParent());
            Path temp = PATH.resolveSibling("dynamicchunkie.json.tmp");
            Files.writeString(temp, GSON.toJson(this));
            try { Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException e) { DynamicChunkie.LOG.error("Cannot save configuration", e); return false; }
    }
}
