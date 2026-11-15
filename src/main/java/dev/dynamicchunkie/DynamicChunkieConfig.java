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
            enableElytraOptimization = true, debugMode = false;
    public int minRenderDistance = 2, maxRenderDistance = 32, defaultRenderDistance = -1;
    public int tunnelDistance = 2, smallCaveDistance = 4, largeCaveDistance = 10,
            undergroundOpenDistance = 12, surfaceDistance = 16, mountainDistance = 24, highAltitudeDistance = 32;
    public int analysisIntervalMs = 750, transitionIntervalMs = 1000, deadZoneChunks = 1;
    public double transitionSpeed = 2, smoothingFactor = 0.35;
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
        transitionIntervalMs = clamp(transitionIntervalMs, 500, 10000);
        deadZoneChunks = clamp(deadZoneChunks, 0, 4);
        transitionSpeed = Double.isFinite(transitionSpeed) ? Math.max(0.25, Math.min(8, transitionSpeed)) : 2;
        smoothingFactor = Double.isFinite(smoothingFactor) ? Math.max(0.05, Math.min(1, smoothingFactor)) : 0.35;
        tunnelDistance = bound(tunnelDistance); smallCaveDistance = bound(smallCaveDistance);
        largeCaveDistance = bound(largeCaveDistance); undergroundOpenDistance = bound(undergroundOpenDistance); surfaceDistance = bound(surfaceDistance);
        mountainDistance = bound(mountainDistance); highAltitudeDistance = bound(highAltitudeDistance);
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
