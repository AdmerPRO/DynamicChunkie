package dev.dynamicchunkie;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.io.IOException;

public final class DynamicChunkieConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("dynamicchunkie.json"); }
    public boolean enableHotSoftSections=false;
    public int hotSoftScanBudgetMicros=1500;
    public boolean protectImportantEntities=true;
    public boolean enableTerrainSectionCulling=false;
    public int terrainChecksPerFrame=128;
    public boolean profilerOverlay=false;
    public boolean enableAdaptiveParticles=false, enableSmartClouds=false;
    public int particleSpawnBudget=100, particleActiveLimit=4000, particleVisualDistance=64;
    public java.util.Map<String,Integer> particleTypeBudgets=new java.util.LinkedHashMap<>();
    public java.util.Set<String> importantParticleTypes=new java.util.HashSet<>(java.util.List.of(
            "minecraft:damage_indicator","minecraft:crit","minecraft:totem_of_undying","minecraft:enchanted_hit"));
    public boolean enableAdaptiveEntityDistance = false, enableEntityTypeLimits = false;
    public int mobVisualDistance=128, playerVisualDistance=256, itemVisualDistance=64,
            armorStandVisualDistance=64, blockEntityVisualDistance=96;
    // Registry IDs; zero means unlimited. Nearest eligible entities are kept first.
    public java.util.Map<String,Integer> entityTypeLimits=new java.util.LinkedHashMap<>();
    public boolean enabled = true, enableAltitudeFactor = true, enableSkyDetection = true,
            enableElytraOptimization = true, debugMode = false, preserveRenderCache = true;
    public boolean enablePrediction = true, enableDirectionalVisibility = true, enableAdaptiveAnalysis = true;
    public boolean enableFpsAdaptive = true, enableHardwareAdaptive = true, enableMemoryManagement = true;
    public boolean enableTeleportRecovery = true, simpleLoadingScreens = true, loadingInputPassthrough = true;
    public int stationaryIntervalMs = 1500, walkingIntervalMs = 750, elytraIntervalMs = 250;
    public int analysisCacheTtlMs = 3000, analysisCacheEntries = 128, cacheMemoryLimitMb = 256;
    public int targetFps = 60, predictionLookaheadMs = 1000, teleportThresholdBlocks = 48, combatGraceMs = 5000;
    public boolean enableEntityCulling = true;
    public int entityCullingBudget = 16, entityCullingDistance = 64;
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
        hotSoftScanBudgetMicros=clamp(hotSoftScanBudgetMicros,250,10000);
        terrainChecksPerFrame=clamp(terrainChecksPerFrame,8,1024);
        particleSpawnBudget=clamp(particleSpawnBudget,1,10000);
        particleActiveLimit=clamp(particleActiveLimit,100,100000);
        particleVisualDistance=clamp(particleVisualDistance,8,512);
        if(particleTypeBudgets==null)particleTypeBudgets=new java.util.LinkedHashMap<>();
        particleTypeBudgets.entrySet().removeIf(e -> e.getKey()==null || e.getValue()==null);
        particleTypeBudgets.replaceAll((key,value)->clamp(value,0,10000));
        if(importantParticleTypes==null)importantParticleTypes=new java.util.HashSet<>();
        mobVisualDistance=clamp(mobVisualDistance,8,512);
        playerVisualDistance=clamp(playerVisualDistance,16,1024);
        itemVisualDistance=clamp(itemVisualDistance,8,512);
        armorStandVisualDistance=clamp(armorStandVisualDistance,8,512);
        blockEntityVisualDistance=clamp(blockEntityVisualDistance,8,512);
        if(entityTypeLimits==null)entityTypeLimits=new java.util.LinkedHashMap<>();
        entityTypeLimits.entrySet().removeIf(e -> e.getKey()==null || e.getValue()==null);
        entityTypeLimits.replaceAll((key,value) -> clamp(value,0,10000));
        stationaryIntervalMs = clamp(stationaryIntervalMs, 250, 10000);
        walkingIntervalMs = clamp(walkingIntervalMs, 250, 5000);
        elytraIntervalMs = clamp(elytraIntervalMs, 100, 2000);
        analysisCacheTtlMs = clamp(analysisCacheTtlMs, 0, 10000);
        analysisCacheEntries = clamp(analysisCacheEntries, 1, 2048);
        cacheMemoryLimitMb = clamp(cacheMemoryLimitMb, 16, 4096);
        targetFps = clamp(targetFps, 15, 240);
        predictionLookaheadMs = clamp(predictionLookaheadMs, 0, 2000);
        teleportThresholdBlocks = clamp(teleportThresholdBlocks, 16, 512);
        combatGraceMs = clamp(combatGraceMs, 1000, 15000);

        entityCullingBudget = clamp(entityCullingBudget, 1, 64);
        entityCullingDistance = clamp(entityCullingDistance, 8, 96);
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
        enableHotSoftSections=other.enableHotSoftSections;hotSoftScanBudgetMicros=other.hotSoftScanBudgetMicros;
        protectImportantEntities=other.protectImportantEntities;
        enableTerrainSectionCulling=other.enableTerrainSectionCulling;terrainChecksPerFrame=other.terrainChecksPerFrame;
        profilerOverlay=other.profilerOverlay;
        enableAdaptiveParticles=other.enableAdaptiveParticles;enableSmartClouds=other.enableSmartClouds;
        particleSpawnBudget=other.particleSpawnBudget;particleActiveLimit=other.particleActiveLimit;
        particleVisualDistance=other.particleVisualDistance;
        particleTypeBudgets=new java.util.LinkedHashMap<>(other.particleTypeBudgets);
        importantParticleTypes=new java.util.HashSet<>(other.importantParticleTypes);
        enableAdaptiveEntityDistance=other.enableAdaptiveEntityDistance;
        enableEntityTypeLimits=other.enableEntityTypeLimits;
        mobVisualDistance=other.mobVisualDistance;playerVisualDistance=other.playerVisualDistance;
        itemVisualDistance=other.itemVisualDistance;armorStandVisualDistance=other.armorStandVisualDistance;
        blockEntityVisualDistance=other.blockEntityVisualDistance;
        entityTypeLimits=new java.util.LinkedHashMap<>(other.entityTypeLimits);
        enablePrediction = other.enablePrediction;
        enableDirectionalVisibility = other.enableDirectionalVisibility;
        enableAdaptiveAnalysis = other.enableAdaptiveAnalysis;
        enableFpsAdaptive = other.enableFpsAdaptive;
        enableHardwareAdaptive = other.enableHardwareAdaptive;
        enableMemoryManagement = other.enableMemoryManagement;
        enableTeleportRecovery = other.enableTeleportRecovery;
        simpleLoadingScreens = other.simpleLoadingScreens;
        loadingInputPassthrough = other.loadingInputPassthrough;
        stationaryIntervalMs = other.stationaryIntervalMs;
        walkingIntervalMs = other.walkingIntervalMs;
        elytraIntervalMs = other.elytraIntervalMs;
        analysisCacheTtlMs = other.analysisCacheTtlMs;
        analysisCacheEntries = other.analysisCacheEntries;
        cacheMemoryLimitMb = other.cacheMemoryLimitMb;
        targetFps = other.targetFps;
        predictionLookaheadMs = other.predictionLookaheadMs;
        teleportThresholdBlocks = other.teleportThresholdBlocks;
        combatGraceMs = other.combatGraceMs;

        enableEntityCulling = other.enableEntityCulling;
        entityCullingBudget = other.entityCullingBudget;
        entityCullingDistance = other.entityCullingDistance;
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
