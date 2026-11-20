package dev.dynamicchunkie;

import dev.dynamicchunkie.runtime.WorkCoordinator;
import dev.dynamicchunkie.runtime.AnalysisScheduler;

import dev.dynamicchunkie.adaptive.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

public final class DynamicDistanceManager {
    final DynamicChunkieConfig config;
    private final EnvironmentAnalyzer analyzer=new EnvironmentAnalyzer();
    private final StableTarget stableTarget=new StableTarget();
    private final DistanceTransitions transitions=new DistanceTransitions();
    private final PerformanceBudget performance=new PerformanceBudget();
    private final EnvironmentCache cache=new EnvironmentCache();
    private static final ThreadMXBean THREADS=ManagementFactory.getThreadMXBean();
    private RenderDistanceController controller;
    private boolean initialized,memoryPressure,wasSampling;
    private Object world;
    private long lastAnalysis,lastFrame;
    private final dev.dynamicchunkie.performance.FrameCpuSampler cpuSampler =
            new dev.dynamicchunkie.performance.FrameCpuSampler(() ->
                    THREADS.isCurrentThreadCpuTimeSupported() ? THREADS.getCurrentThreadCpuTime() : -1);
    private double smoothed=Double.NaN,lastYaw,analysisMs;
    private DistanceOwnership ownership;
    private int target,intervalMs=750;
    private @Nullable EnvironmentScore score;

    public DynamicDistanceManager(DynamicChunkieConfig config) {this.config=config;}
    public void onFrame(Minecraft client) {
        var settings = DynamicChunkie.settings();
        boolean sampling=(settings.enabled && (settings.enableFpsAdaptive || settings.enableHardwareAdaptive ||
                settings.enableAdaptiveEntityDistance || settings.enableAdaptiveParticles || settings.enableSmartClouds) ||
                settings.profilerOverlay || settings.debugMode || settings.diagnosticsEnabled) && client.level!=null && client.player!=null &&
                client.gui.screen()==null && !client.isPaused();
        if(!sampling) {
            if(wasSampling)performance.reset();
            wasSampling=false;lastFrame=0;cpuSampler.reset();
            return;
        }
        long now=System.nanoTime();
        if (lastFrame == 0) cpuSampler.reset();
        double cpu = cpuSampler.frame(now, settings.cpuSampleIntervalMs * 1_000_000L);
        if (wasSampling && lastFrame!=0) {
            performance.record((now-lastFrame)/1e6,client.getFrameTimeNs()/1e6,
                    cpu,client.getGpuUtilization());
            performance.summarize(now);
        } else if (wasSampling) performance.reset();
        wasSampling=true;
        lastFrame=now;
    }
    public void noteCombat() {transitions.combat(System.nanoTime());}
    public void tick(Minecraft client) {
        if (!initialized) {
            controller=new RenderDistanceController(client);
            config.minRenderDistance=Math.min(config.minRenderDistance,controller.supportedMaximum());
            config.maxRenderDistance=Math.min(config.maxRenderDistance,controller.supportedMaximum());
            config.validate();
            ownership = new DistanceOwnership(config, controller::current, controller::apply, client.options::save, config::save);
            if (!ownership.recover()) return;
            if (config.defaultRenderDistance<2) config.defaultRenderDistance=controller.current();
            config.save();DynamicChunkie.publishSettings();initialized=true;
        }
        var settings = DynamicChunkie.settings();
        if (!settings.enabled || !RenderCache.canChangeDistance() || client.level==null || client.player==null) {
            restore(client);return;
        }
        if (!ownership.captureManualChange()) return;
        long now=System.nanoTime();
        if (client.player.hurtTime>0) transitions.combat(now);
        if (client.gui.screen()!=null || client.isPaused()) {transitions.pause(now);return;}
        if (!ownership.owned()) {
            if (!ownership.acquire()) return;
            transitions.pause(now);
        }
        if (world!=client.level) {
            world=client.level;resetAnalysis();performance.reset();lastFrame=0;
        }
        var player=client.player;
        if (transitions.observe(client.level,player.getX(),player.getY(),player.getZ(),player.getDeltaMovement().length(),
                player.hurtTime>0,now,settings.enableTeleportRecovery,settings.teleportThresholdBlocks,settings.combatGraceMs)) {
            ownership.set(settings.bound(2));
            resetAnalysis();performance.reset();lastFrame=0;
        }
        double speed=player.getDeltaMovement().length()*20;
        boolean turning=Math.abs(player.getYRot()-lastYaw)>15;
        intervalMs=AnalysisPolicy.interval(speed,player.isFallFlying(),turning,
                settings.enableHardwareAdaptive && (performance.cpuPressure() || analysisMs>3),
                settings.stationaryIntervalMs,settings.walkingIntervalMs,settings.elytraIntervalMs,
                settings.enableAdaptiveAnalysis,settings.analysisIntervalMs);
        if ((score==null || now-lastAnalysis>=intervalMs*1_000_000L) && WorkCoordinator.admit(AnalysisScheduler.Module.ENVIRONMENT, 0, true)) {
            long begin=System.nanoTime();
            var eye=player.getEyePosition();
            var key=new EnvironmentCache.Key((int)Math.floor(eye.x*4),(int)Math.floor(eye.y*4),
                    (int)Math.floor(eye.z*4),(int)Math.floor(player.getYRot()/5),(int)Math.floor(player.getXRot()/5));
            long revision=((EnvironmentRevision)client.level).dynamicchunkie$revision();
            boolean reusable=settings.enableAdaptiveAnalysis && speed<.15;
            EnvironmentScore cached=reusable?cache.get(key,now,revision,settings.analysisCacheTtlMs):null;
            score=cached!=null?cached:analyzer.analyze(client,settings);
            if (cached==null && reusable) cache.put(key,score,now,revision,settings.analysisCacheEntries,
                    settings.analysisMetadataLimitKiB*1024L);
            if (cached==null) analysisMs=analysisMs*.8+(System.nanoTime()-begin)/1e6*.2;
            smoothed=Double.isNaN(smoothed)?score.openness():smoothed+settings.smoothingFactor*(score.openness()-smoothed);
            if(settings.enablePrediction && AnalysisPolicy.approachingWall(score.elytra(),score.speed(),score.forward()))
                smoothed=Math.min(smoothed,score.openness());
            lastAnalysis=now;lastYaw=player.getYRot();
            WorkCoordinator.complete(AnalysisScheduler.Module.ENVIRONMENT, begin, cached == null ? 0 : 1);
        }
        if (score == null) return;
        int candidate=EnvironmentAnalyzer.target(score,smoothed,settings);
        int effectiveFps=Math.max(15,Math.min(settings.targetFps,client.options.framerateLimit().get()));
        candidate=performance.adjust(candidate,controller.current(),settings.minRenderDistance,settings.maxRenderDistance,
                effectiveFps,now,settings.enableFpsAdaptive,settings.enableHardwareAdaptive);
        Runtime runtime=Runtime.getRuntime();
        memoryPressure=settings.enableMemoryManagement &&
                MemoryBudget.heapPressure(runtime.totalMemory()-runtime.freeMemory(),runtime.maxMemory());
        if (memoryPressure) cache.clear();
        candidate=MemoryBudget.constrain(candidate,controller.current(),settings.minRenderDistance,memoryPressure);
        if (settings.enableMemoryManagement)
            candidate=Math.min(candidate,MemoryBudget.reservationRadius(settings.minRenderDistance,settings.maxRenderDistance,
                    client.level.getSectionsCount(),settings.cacheMemoryLimitMb));
        target=stableTarget.update(settings.bound(candidate),now,settings);
        if(settings.enablePrediction && AnalysisPolicy.approachingWall(score.elytra(),score.speed(),score.forward()))
            target=stableTarget.restrict(candidate,settings);
        int current=controller.current(),bounded=settings.bound(current);
        if (current!=bounded) {ownership.set(bounded);return;}
        if (Math.abs(target-current)<=settings.deadZoneChunks) return;
        int next=transitions.next(current,target,now,settings.transitionIntervalMs);
        if (next!=current) {ownership.set(settings.bound(next));}
    }
    private void resetAnalysis() {
        score=null;cache.clear();stableTarget.reset();smoothed=Double.NaN;lastAnalysis=0;
    }
    public void restore(Minecraft client) {
        if (ownership == null || !ownership.owned() || !ownership.release()) return;
        world=null;target=0;resetAnalysis();cache.release();transitions.reset();performance.reset();lastFrame=0;
    }
    public boolean toggle(Minecraft client) {
        var draft = config.copy();
        draft.enabled = !draft.enabled;
        return applyConfig(client, draft);
    }
    public boolean applyConfig(Minecraft client,DynamicChunkieConfig draft) {
        draft.validate();var old=config.copy();
        if (!draft.enabled) restore(client);
        config.copySettingsFrom(draft);
        int maximum=new RenderDistanceController(client).supportedMaximum();
        config.minRenderDistance=Math.min(config.minRenderDistance,maximum);
        config.maxRenderDistance=Math.min(config.maxRenderDistance,maximum);config.validate();
        if (!config.save()) {config.copySettingsFrom(old);return false;}
        DynamicChunkie.publishSettings();
        dev.dynamicchunkie.visual.VisualRules.configure(config);
        resetAnalysis();performance.reset();return true;
    }
    public void unload(Object level, int x, int z) {
        if (world == level) cache.removeChunk(x, z);
    }

    public void maintainCache() {
        if (cache.size() > 0 && WorkCoordinator.admit(AnalysisScheduler.Module.CACHE_MAINTENANCE, 100_000_000L, false)) {
            long now = System.nanoTime();
            int removed = cache.expire(now, DynamicChunkie.settings().analysisCacheTtlMs, 8);
            WorkCoordinator.complete(AnalysisScheduler.Module.CACHE_MAINTENANCE, now, removed);
        }
    }
    public int target() {return target;}
    public @Nullable EnvironmentScore score() {return score;}
    public long analysisAgeMs() {return (System.nanoTime()-lastAnalysis)/1_000_000;}
    public int analysisIntervalMs() {return intervalMs;}
    public long cacheHits() {return cache.hits();}
    public long cacheBytes() {return cache.chargedBytes();}
    public PerformanceBudget performance() {return performance;}
    public boolean recovering() {return transitions.recovering();}
    public boolean memoryPressure() {return memoryPressure;}
}
