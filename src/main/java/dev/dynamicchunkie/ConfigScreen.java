package dev.dynamicchunkie;

import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native Minecraft widgets; Mod Menu is optional and no config-library dependency is needed. */
public final class ConfigScreen extends Screen {
    private final Screen parent;
    private DynamicChunkieConfig draft = DynamicChunkie.config().copy();
    private final Set<String> invalid = new HashSet<>();
    private final List<String> labels = new ArrayList<>();
    private int page, row, left, rowWidth;
    private Component error;
    private java.util.List<net.minecraft.world.entity.EntityType<?>> cachedTypes;
    private java.util.List<net.minecraft.core.particles.ParticleType<?>> cachedParticles;

    public ConfigScreen(Screen parent) {
        super(Component.literal("DynamicChunkie"));
        this.parent = parent;
    }
    private Component text(String key) {
        if(key.startsWith("entityLimit:"))return Component.translatable("dynamicchunkie.config.entityLimit",key.substring(12));
        if(key.startsWith("particleBudget:"))return Component.translatable("dynamicchunkie.config.particleBudget",key.substring(15));
        if(key.startsWith("particleImportant:"))return Component.translatable("dynamicchunkie.config.particleImportant",key.substring(18));
        return Component.translatable("dynamicchunkie.config." + key);
    }
    private java.util.List<net.minecraft.world.entity.EntityType<?>> types() {
        if(cachedTypes==null)cachedTypes=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.stream()
                .filter(t->!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t).toString().equals("minecraft:player"))
                .sorted(java.util.Comparator.comparing(t -> group(t)+":"+net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t))).toList();
        return cachedTypes;
    }
    private String group(net.minecraft.world.entity.EntityType<?> type) {
        String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        if(java.util.Set.of("enderman","endermite","ender_dragon","shulker").contains(id))return "End";
        if(java.util.Set.of("blaze","ghast","piglin","piglin_brute","hoglin","zoglin","strider","magma_cube","wither_skeleton","zombified_piglin").contains(id))return "Nether";
        if(java.util.Set.of("cow","pig","sheep","chicken","rabbit","mooshroom").contains(id))return "Farm";
        return switch(type.getCategory().getName()) {
            case "creature" -> "Animals";
            case "monster" -> "Monsters";
            case "water_creature","water_ambient","underground_water_creature","axolotls" -> "Water";
            default -> "Misc";
        };
    }
    private java.util.List<net.minecraft.core.particles.ParticleType<?>> particleTypes() {
        if(cachedParticles==null)cachedParticles=net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.stream()
                .sorted(java.util.Comparator.comparing(t->net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(t).toString())).toList();
        return cachedParticles;
    }
    private int particleStart() {return 14+(types().size()+4)/5;}
    private int toolsStart() {return particleStart()+(particleTypes().size()+2)/3;}
    private int pageCount() {return toolsStart()+3;}
    @Override protected void init() {
        labels.clear();
        row = 0;
        rowWidth = Math.min(340, width - 20);
        left = (width - rowWidth) / 2;
        addRenderableWidget(Button.builder(text("previous"), b -> changePage(-1))
                .bounds(left, 39, 55, 20).build());
        addRenderableWidget(Button.builder(text("next"), b -> changePage(1))
                .bounds(left + rowWidth - 55, 39, 55, 20).build());
        switch (page) {
            case 13 -> {
                toggle("enableTerrainSectionCulling",()->draft.enableTerrainSectionCulling,v->draft.enableTerrainSectionCulling=v);
                integer("terrainChecksPerFrame",draft.terrainChecksPerFrame,v->draft.terrainChecksPerFrame=v);
                toggle("enableHotSoftSections",()->draft.enableHotSoftSections,v->draft.enableHotSoftSections=v);
                integer("hotSoftScanBudgetMicros",draft.hotSoftScanBudgetMicros,v->draft.hotSoftScanBudgetMicros=v);
                addRenderableWidget(Button.builder(text("particleTypes"),b->{
                    if(!invalid.isEmpty()) {error=text("invalid");return;}
                    page=particleStart();rebuildWidgets();
                }).bounds(left,162,rowWidth/2-2,20).build());
                addRenderableWidget(Button.builder(text("performanceTools"),b->{
                    if (!invalid.isEmpty()) {error=text("invalid");return;}
                    page=toolsStart();rebuildWidgets();
                }).bounds(left+rowWidth/2,162,rowWidth/2,20).build());
            }
            case 10 -> {
                toggle("enableAdaptiveEntityDistance",()->draft.enableAdaptiveEntityDistance,v->draft.enableAdaptiveEntityDistance=v);
                integer("mobVisualDistance",draft.mobVisualDistance,v->draft.mobVisualDistance=v);
                integer("playerVisualDistance",draft.playerVisualDistance,v->draft.playerVisualDistance=v);
                integer("itemVisualDistance",draft.itemVisualDistance,v->draft.itemVisualDistance=v);
                integer("armorStandVisualDistance",draft.armorStandVisualDistance,v->draft.armorStandVisualDistance=v);
                integer("blockEntityVisualDistance",draft.blockEntityVisualDistance,v->draft.blockEntityVisualDistance=v);
            }
            case 12 -> {
                toggle("enableAdaptiveParticles",()->draft.enableAdaptiveParticles,v->draft.enableAdaptiveParticles=v);
                integer("particleSpawnBudget",draft.particleSpawnBudget,v->draft.particleSpawnBudget=v);
                integer("particleActiveLimit",draft.particleActiveLimit,v->draft.particleActiveLimit=v);
                integer("particleVisualDistance",draft.particleVisualDistance,v->draft.particleVisualDistance=v);
                toggle("enableSmartClouds",()->draft.enableSmartClouds,v->draft.enableSmartClouds=v);
                toggle("profilerOverlay",()->draft.profilerOverlay,v->draft.profilerOverlay=v);
            }
            case 11 -> {
                toggle("enableEntityTypeLimits",()->draft.enableEntityTypeLimits,v->draft.enableEntityTypeLimits=v);
                toggle("protectImportantEntities",()->draft.protectImportantEntities,v->draft.protectImportantEntities=v);
                String[] groups={"Animals","Monsters","Water","End","Nether","Farm","Misc"};
                for(int i=0;i<groups.length;i++) {
                    String category=groups[i];int column=i%3,line=i/3;
                    addRenderableWidget(Button.builder(Component.literal(category),b->{
                        var entries=types();
                        for(int k=0;k<entries.size();k++)if(group(entries.get(k)).equals(category)) {
                            if(!invalid.isEmpty()) {error=text("invalid");return;}
                            page=14+k/5;rebuildWidgets();return;
                        }
                    }).bounds(left+column*(rowWidth/3),116+line*24,rowWidth/3-4,20).build());
                }
            }
            default -> {
                if (page == toolsStart()) {
                    toggle("diagnosticsEnabled",()->draft.diagnosticsEnabled,v->draft.diagnosticsEnabled=v);
                    toggle("enableJfrEvents",()->draft.enableJfrEvents,v->draft.enableJfrEvents=v);
                    toggle("enableAdaptiveModuleBudgets",()->draft.enableAdaptiveModuleBudgets,v->draft.enableAdaptiveModuleBudgets=v);
                    integer("hudRefreshMs",draft.hudRefreshMs,v->draft.hudRefreshMs=v);
                    integer("cpuSampleIntervalMs",draft.cpuSampleIntervalMs,v->draft.cpuSampleIntervalMs=v);
                    integer("sharedAnalysisBudgetMicros",draft.sharedAnalysisBudgetMicros,v->draft.sharedAnalysisBudgetMicros=v);
                } else if (page == toolsStart()+1) {
                    integer("analysisMetadataLimitKiB",draft.analysisMetadataLimitKiB,v->draft.analysisMetadataLimitKiB=v);
                    integer("entityMetadataLimitKiB",draft.entityMetadataLimitKiB,v->draft.entityMetadataLimitKiB=v);
                    integer("coldMetadataLimitKiB",draft.coldMetadataLimitKiB,v->draft.coldMetadataLimitKiB=v);
                    var eviction = toggle("enableColdGeometryEviction",()->draft.enableColdGeometryEviction,v->draft.enableColdGeometryEviction=v);
                    eviction.active=dev.dynamicchunkie.render.RendererCapabilities.current().coldMeshRetirement();
                    integer("coldGeometryGraceMs",draft.coldGeometryGraceMs,v->draft.coldGeometryGraceMs=v);
                    toggle("enableAsyncSelection",()->draft.enableAsyncSelection,v->draft.enableAsyncSelection=v);
                } else if (page == toolsStart()+2) {
                    integer("entityShadowDistance",draft.entityShadowDistance,v->draft.entityShadowDistance=v);
                    integer("entityNameDistance",draft.entityNameDistance,v->draft.entityNameDistance=v);
                    integer("mobDetailDistance",draft.mobDetailDistance,v->draft.mobDetailDistance=v);
                    toggle("shortenAmbientParticles",()->draft.shortenAmbientParticles,v->draft.shortenAmbientParticles=v);
                    toggle("filterHotSectionDraws",()->draft.filterHotSectionDraws,v->draft.filterHotSectionDraws=v);
                    toggle("deferColdSectionRebuilds",()->draft.deferColdSectionRebuilds,v->draft.deferColdSectionRebuilds=v);
                } else if(page<particleStart()) {
                    var types=types();int start=(page-14)*5;
                    for(int i=start;i<Math.min(types.size(),start+5);i++) {
                        var type=types.get(i);String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                        integer("entityLimit:"+group(type)+" / "+id,draft.entityTypeLimits.getOrDefault(id,0),v->draft.entityTypeLimits.put(id,v));
                    }
                } else {
                    var types=particleTypes();int start=(page-particleStart())*3;
                    for(int i=start;i<Math.min(types.size(),start+3);i++) {
                        var type=types.get(i);String id=net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(type).toString();
                        integer("particleBudget:"+id,draft.particleTypeBudgets.getOrDefault(id,draft.particleSpawnBudget),v->draft.particleTypeBudgets.put(id,v));
                        var button=toggle("particleImportant:"+id,()->type.getOverrideLimiter() || draft.importantParticleTypes.contains(id),v->{
                            if(v)draft.importantParticleTypes.add(id);else draft.importantParticleTypes.remove(id);
                        });
                        if(type.getOverrideLimiter())button.active=false;
                    }
                }
            }
            case 0 -> {
                toggle("enabled", () -> draft.enabled, v -> draft.enabled = v);
                toggle("preserveRenderCache", () -> draft.preserveRenderCache, v -> draft.preserveRenderCache = v);
                toggle("enableAltitudeFactor", () -> draft.enableAltitudeFactor, v -> draft.enableAltitudeFactor = v);
                toggle("enableSkyDetection", () -> draft.enableSkyDetection, v -> draft.enableSkyDetection = v);
                toggle("enableElytraOptimization", () -> draft.enableElytraOptimization, v -> draft.enableElytraOptimization = v);
                toggle("debugMode", () -> draft.debugMode, v -> draft.debugMode = v);
            }
            case 1 -> {
                integer("minRenderDistance", draft.minRenderDistance, v -> draft.minRenderDistance = v);
                integer("maxRenderDistance", draft.maxRenderDistance, v -> draft.maxRenderDistance = v);
                integer("tunnelDistance", draft.tunnelDistance, v -> draft.tunnelDistance = v);
                integer("smallCaveDistance", draft.smallCaveDistance, v -> draft.smallCaveDistance = v);
                integer("largeCaveDistance", draft.largeCaveDistance, v -> draft.largeCaveDistance = v);
                integer("undergroundOpenDistance", draft.undergroundOpenDistance, v -> draft.undergroundOpenDistance = v);
            }
            case 2 -> {
                integer("surfaceDistance", draft.surfaceDistance, v -> draft.surfaceDistance = v);
                integer("mountainDistance", draft.mountainDistance, v -> draft.mountainDistance = v);
                integer("highAltitudeDistance", draft.highAltitudeDistance, v -> draft.highAltitudeDistance = v);
            }
            case 4 -> {
                toggle("enableEntityCulling", () -> draft.enableEntityCulling, v -> draft.enableEntityCulling = v);
                integer("entityCullingBudget", draft.entityCullingBudget, v -> draft.entityCullingBudget = v);
                integer("entityCullingDistance", draft.entityCullingDistance, v -> draft.entityCullingDistance = v);
            }
            case 5 -> {
                toggle("enablePrediction", () -> draft.enablePrediction, v -> draft.enablePrediction=v);
                toggle("enableDirectionalVisibility", () -> draft.enableDirectionalVisibility, v -> draft.enableDirectionalVisibility=v);
                integer("predictionLookaheadMs", draft.predictionLookaheadMs, v -> draft.predictionLookaheadMs=v);
            }
            case 6 -> {
                toggle("enableAdaptiveAnalysis", () -> draft.enableAdaptiveAnalysis, v -> draft.enableAdaptiveAnalysis=v);
                integer("stationaryIntervalMs", draft.stationaryIntervalMs, v -> draft.stationaryIntervalMs=v);
                integer("walkingIntervalMs", draft.walkingIntervalMs, v -> draft.walkingIntervalMs=v);
                integer("elytraIntervalMs", draft.elytraIntervalMs, v -> draft.elytraIntervalMs=v);
                integer("analysisCacheTtlMs", draft.analysisCacheTtlMs, v -> draft.analysisCacheTtlMs=v);
            }
            case 7 -> {
                toggle("enableFpsAdaptive", () -> draft.enableFpsAdaptive, v -> draft.enableFpsAdaptive=v);
                toggle("enableHardwareAdaptive", () -> draft.enableHardwareAdaptive, v -> draft.enableHardwareAdaptive=v);
                integer("targetFps", draft.targetFps, v -> draft.targetFps=v);
                toggle("enableMemoryManagement", () -> draft.enableMemoryManagement, v -> draft.enableMemoryManagement=v);
                integer("cacheMemoryLimitMb", draft.cacheMemoryLimitMb, v -> draft.cacheMemoryLimitMb=v);
                integer("analysisCacheEntries", draft.analysisCacheEntries, v -> draft.analysisCacheEntries=v);
            }
            case 8 -> {
                toggle("enableTeleportRecovery", () -> draft.enableTeleportRecovery, v -> draft.enableTeleportRecovery=v);
                integer("teleportThresholdBlocks", draft.teleportThresholdBlocks, v -> draft.teleportThresholdBlocks=v);
                integer("combatGraceMs", draft.combatGraceMs, v -> draft.combatGraceMs=v);
            }
            case 9 -> {
                toggle("simpleLoadingScreens", () -> draft.simpleLoadingScreens, v -> draft.simpleLoadingScreens=v);
                toggle("loadingInputPassthrough", () -> draft.loadingInputPassthrough, v -> draft.loadingInputPassthrough=v);
            }
            case 3 -> {
                integer("analysisIntervalMs", draft.analysisIntervalMs, v -> draft.analysisIntervalMs = v);
                integer("transitionIntervalMs", draft.transitionIntervalMs, v -> draft.transitionIntervalMs = v);
                decimal("smoothingFactor", draft.smoothingFactor, v -> draft.smoothingFactor = v);
                integer("deadZoneChunks", draft.deadZoneChunks, v -> draft.deadZoneChunks = v);
                integer("targetStabilityMs", draft.targetStabilityMs, v -> draft.targetStabilityMs = v);
            }
        }
        int buttonWidth = (rowWidth - 8) / 3;
        addRenderableWidget(Button.builder(text("cancel"), b -> onClose()).bounds(left, height - 27, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(text("reset"), b -> {
            draft = new DynamicChunkieConfig();
            invalid.clear();
            error = null;
            rebuildWidgets();
        }).bounds(left + buttonWidth + 4, height - 27, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(text("save"), b -> save())
                .bounds(left + 2 * (buttonWidth + 4), height - 27, buttonWidth, 20).build());
    }
    private void changePage(int delta) {
        if (!invalid.isEmpty()) { error = text("invalid"); return; }
        page = Math.floorMod(page + delta, pageCount());
        error = null;
        rebuildWidgets();
    }
    private Button toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
        int y = 66 + row++ * 22;
        var button=addRenderableWidget(Button.builder(toggleText(key, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(toggleText(key, get.getAsBoolean()));
        }).bounds(left, y, rowWidth, 20).build());
        labels.add(null);return button;
    }
    private Component toggleText(String key, boolean value) {
        return text(key).copy().append(": ").append(Component.translatable(value ? "options.on" : "options.off"));
    }
    private void integer(String key, int value, IntConsumer set) {
        number(key, Integer.toString(value), input -> set.accept(Integer.parseInt(input)));
    }
    private void decimal(String key, double value, DoubleConsumer set) {
        number(key, Double.toString(value), input -> {
            double parsed = Double.parseDouble(input.replace(',', '.'));
            if (!Double.isFinite(parsed)) throw new NumberFormatException();
            set.accept(parsed);
        });
    }
    private void number(String key, String value, Consumer<String> set) {
        int y = 66 + row++ * 22;
        var field = new EditBox(font, left + rowWidth - 90, y, 90, 20, text(key));
        field.setTooltip(Tooltip.create(text(key)));
        field.setMaxLength(16);
        field.setValue(value);
        field.setResponder(input -> {
            try { set.accept(input); invalid.remove(key); field.setTextColor(0xFFFFFFFF); error = null; }
            catch (NumberFormatException e) { invalid.add(key); field.setTextColor(0xFFFF5555); error = text("invalid"); }
        });
        addRenderableWidget(field);
        labels.add(key);
    }
    private void save() {
        if (!invalid.isEmpty()) { error = text("invalid"); return; }
        if (DynamicChunkie.applyConfig(draft)) onClose();
        else error = text("saveFailed");
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(font, title, (width - font.width(title)) / 2, 10, 0xFFFFFFFF);
        Component pageTitle = page>=toolsStart()?text("performanceTools"):page>=particleStart()?text("particleTypes"):page>=14?Component.literal("Entity limits / "+(page-13)):text("page." + page);
        graphics.text(font, pageTitle, (width - font.width(pageTitle)) / 2, 45, 0xFFFFFFFF);
        for (int i = 0; i < labels.size(); i++)
            if (labels.get(i) != null) graphics.text(font, font.plainSubstrByWidth(text(labels.get(i)).getString(),rowWidth-98), left, 72 + i * 22, 0xFFFFFFFF);
        if (error != null) graphics.text(font, error, left, height - 39, 0xFFFF5555);
    }
}
