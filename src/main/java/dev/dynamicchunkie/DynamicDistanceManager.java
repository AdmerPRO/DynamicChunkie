package dev.dynamicchunkie;

import dev.dynamicchunkie.adaptive.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import net.minecraft.client.Minecraft;

public final class DynamicDistanceManager {
    final DynamicChunkieConfig config;
    private final EnvironmentAnalyzer analyzer=new EnvironmentAnalyzer();
    private final StableTarget stableTarget=new StableTarget();
    private final DistanceTransitions transitions=new DistanceTransitions();
    private final PerformanceBudget performance=new PerformanceBudget();
    private final EnvironmentCache cache=new EnvironmentCache();
    private static final ThreadMXBean THREADS=ManagementFactory.getThreadMXBean();
    private final dev.dynamicchunkie.performance.FrameCpuSampler cpuSampler =
            new dev.dynamicchunkie.performance.FrameCpuSampler(() ->
                    THREADS.isCurrentThreadCpuTimeSupported() ? THREADS.getCurrentThreadCpuTime() : -1);
    private RenderDistanceController controller;
    private DistanceOwnership ownership;
    private boolean initialized,memoryPressure,wasSampling;
    private Object world;
    private long lastAnalysis,lastFrame;
    private double smoothed=Double.NaN,lastYaw,analysisMs;
    private int target,intervalMs=750;
    private EnvironmentScore score;

    public DynamicDistanceManager(DynamicChunkieConfig config) {this.config=config;}
    public void onFrame(Minecraft client) {
        long now=System.nanoTime();
        boolean sampling=config.enabled && client.level!=null && client.player!=null &&
                client.screen==null && !client.isPaused();
        if (!sampling || lastFrame==0) cpuSampler.reset();
        double cpu=sampling ? cpuSampler.frame(now,250_000_000L) : Double.NaN;
        if (sampling && wasSampling && lastFrame!=0) {
            performance.record((now-lastFrame)/1e6,client.getFrameTimeNs()/1e6,
                    cpu,client.getGpuUtilization());
        } else if (wasSampling) performance.reset();
        wasSampling=sampling;
        lastFrame=now;
    }
    public void noteCombat() {transitions.combat(System.nanoTime());}
    public void tick(Minecraft client) {
        if (!initialized) {
            controller=new RenderDistanceController(client);
            config.minRenderDistance=Math.min(config.minRenderDistance,controller.supportedMaximum());
            config.maxRenderDistance=Math.min(config.maxRenderDistance,controller.supportedMaximum());
            config.validate();
            ownership=new DistanceOwnership(config,controller::current,controller::apply,client.options::save,config::save);
            if (!ownership.recover()) return;
            if (config.defaultRenderDistance<2) config.defaultRenderDistance=controller.current();
            config.save();initialized=true;
        }
        if (!config.enabled || !RenderCache.canChangeDistance() || client.level==null || client.player==null) {
            restore(client);return;
        }
        if (!ownership.captureManualChange()) return;
        long now=System.nanoTime();
        if (client.player.hurtTime>0) transitions.combat(now);
        if (client.screen!=null || client.isPaused()) {transitions.pause(now);return;}
        if (!ownership.owned()) {
            if (!ownership.acquire()) return;
            transitions.pause(now);
        }
        if (world!=client.level) {
            world=client.level;resetAnalysis();performance.reset();lastFrame=0;
        }
        var player=client.player;
        if (transitions.observe(client.level,player.getX(),player.getY(),player.getZ(),player.getDeltaMovement().length(),
                player.hurtTime>0,now,config.enableTeleportRecovery,config.teleportThresholdBlocks,config.combatGraceMs)) {
            ownership.set(config.bound(2));
            resetAnalysis();performance.reset();lastFrame=0;
        }
        double speed=player.getDeltaMovement().length()*20;
        boolean turning=Math.abs(net.minecraft.util.Mth.wrapDegrees(player.getYRot()-lastYaw))>15;
        intervalMs=AnalysisPolicy.interval(speed,player.isFallFlying(),turning,
                config.enableHardwareAdaptive && (performance.cpuPressure() || analysisMs>3),
                config.stationaryIntervalMs,config.walkingIntervalMs,config.elytraIntervalMs,
                config.enableAdaptiveAnalysis,config.analysisIntervalMs);
        if (score==null || now-lastAnalysis>=intervalMs*1_000_000L) {
            long begin=System.nanoTime();
            var eye=player.getEyePosition();
            var key=new EnvironmentCache.Key((int)Math.floor(eye.x*4),(int)Math.floor(eye.y*4),
                    (int)Math.floor(eye.z*4),(int)Math.floor(net.minecraft.util.Mth.wrapDegrees(player.getYRot())/5),(int)Math.floor(player.getXRot()/5));
            int x=(int)Math.floor(eye.x)>>4,y=(int)Math.floor(eye.y)>>4,z=(int)Math.floor(eye.z)>>4;
            long revision=((EnvironmentRevision)client.level).dynamicchunkie$changes().region(x-4,y-4,z-4,x+4,y+4,z+4,true);
            boolean reusable=config.enableAdaptiveAnalysis && speed<.15;
            EnvironmentScore cached=reusable?cache.get(key,now,revision,config.analysisCacheTtlMs):null;
            score=cached!=null?cached:analyzer.analyze(client,config);
            if (cached==null && reusable) cache.put(key,score,now,revision,config.analysisCacheEntries,
                    config.cacheMemoryLimitMb*1024L*1024);
            if (cached==null) analysisMs=analysisMs*.8+(System.nanoTime()-begin)/1e6*.2;
            smoothed=Double.isNaN(smoothed)?score.openness():smoothed+config.smoothingFactor*(score.openness()-smoothed);
            lastAnalysis=now;lastYaw=player.getYRot();
        }
        int candidate=EnvironmentAnalyzer.target(score,smoothed,config);
        int effectiveFps=Math.max(15,Math.min(config.targetFps,client.options.framerateLimit().get()));
        candidate=performance.adjust(candidate,controller.current(),config.minRenderDistance,config.maxRenderDistance,
                effectiveFps,now,config.enableFpsAdaptive,config.enableHardwareAdaptive);
        Runtime runtime=Runtime.getRuntime();
        memoryPressure=config.enableMemoryManagement &&
                MemoryBudget.heapPressure(runtime.totalMemory()-runtime.freeMemory(),runtime.maxMemory());
        if (memoryPressure) cache.clear();
        candidate=MemoryBudget.constrain(candidate,controller.current(),config.minRenderDistance,memoryPressure);
        if (config.enableMemoryManagement)
            candidate=Math.min(candidate,MemoryBudget.reservationRadius(config.minRenderDistance,config.maxRenderDistance,
                    client.level.getSectionsCount(),config.cacheMemoryLimitMb));
        target=stableTarget.update(config.bound(candidate),now,config);
        int current=controller.current(),bounded=config.bound(current);
        if (current!=bounded) {ownership.set(bounded);return;}
        if (target>current && (config.enableFpsAdaptive || config.enableHardwareAdaptive) && performance.growthBlocked(now)) return;
        if (Math.abs(target-current)<=config.deadZoneChunks) return;
        int next=transitions.next(current,target,now,config.transitionIntervalMs);
        if (next!=current) {ownership.set(config.bound(next));}
    }
    private void resetAnalysis() {
        analyzer.reset();score=null;cache.clear();stableTarget.reset();smoothed=Double.NaN;lastAnalysis=0;
    }
    public void restore(Minecraft client) {
        if (ownership==null || !ownership.owned() || !ownership.release()) return;
        world=null;target=0;resetAnalysis();transitions.reset();performance.reset();lastFrame=0;
    }
    public void toggle(Minecraft client) {
        config.enabled=!config.enabled;if (!config.enabled) restore(client);config.save();
    }
    public boolean applyConfig(Minecraft client,DynamicChunkieConfig draft) {
        draft.validate();var old=config.copy();
        if (!draft.enabled) restore(client);
        config.copySettingsFrom(draft);
        int maximum=new RenderDistanceController(client).supportedMaximum();
        config.minRenderDistance=Math.min(config.minRenderDistance,maximum);
        config.maxRenderDistance=Math.min(config.maxRenderDistance,maximum);config.validate();
        if (!config.save()) {config.copySettingsFrom(old);return false;}
        resetAnalysis();performance.reset();return true;
    }
    public int target() {return target;}
    public EnvironmentScore score() {return score;}
    public long analysisAgeMs() {return (System.nanoTime()-lastAnalysis)/1_000_000;}
    public int analysisIntervalMs() {return intervalMs;}
    public long cacheHits() {return cache.hits();}
    public int cacheEntries() {return cache.size();}
    public PerformanceBudget performance() {return performance;}
    public boolean recovering() {return transitions.recovering();}
    public boolean memoryPressure() {return memoryPressure;}
}
