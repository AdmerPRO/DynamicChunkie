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
    private RenderDistanceController controller;
    private boolean active,initialized,memoryPressure,wasSampling;
    private Object world;
    private long lastAnalysis,lastFrame,lastCpu;
    private double smoothed=Double.NaN,lastYaw,analysisMs;
    private int lastApplied,target,intervalMs=750;
    private EnvironmentScore score;

    public DynamicDistanceManager(DynamicChunkieConfig config) {this.config=config;}
    public void onFrame(Minecraft client) {
        long now=System.nanoTime();
        long cpu=THREADS.isCurrentThreadCpuTimeSupported()?THREADS.getCurrentThreadCpuTime():-1;
        boolean sampling=config.enabled && client.level!=null && client.player!=null &&
                client.gui.screen()==null && !client.isPaused();
        if (sampling && wasSampling && lastFrame!=0) {
            performance.record((now-lastFrame)/1e6,client.getFrameTimeNs()/1e6,
                    cpu>=0 && lastCpu>=0?(cpu-lastCpu)/1e6:Double.NaN,client.getGpuUtilization());
        } else if (wasSampling) performance.reset();
        wasSampling=sampling;
        lastFrame=now;lastCpu=cpu;
    }
    public void noteCombat() {transitions.combat(System.nanoTime());}
    public void tick(Minecraft client) {
        if (!initialized) {
            controller=new RenderDistanceController(client);
            config.minRenderDistance=Math.min(config.minRenderDistance,controller.supportedMaximum());
            config.maxRenderDistance=Math.min(config.maxRenderDistance,controller.supportedMaximum());
            config.validate();
            if (config.restorePending && config.defaultRenderDistance>=2) {
                controller.apply(config.defaultRenderDistance);
                client.options.save();config.restorePending=false;
            }
            if (config.defaultRenderDistance<2) config.defaultRenderDistance=controller.current();
            config.save();initialized=true;
        }
        if (!config.enabled || !RenderCache.canChangeDistance() || client.level==null || client.player==null) {
            restore(client);return;
        }
        if (active && controller.current()!=lastApplied) {
            config.defaultRenderDistance=controller.current();
            config.save();lastApplied=controller.current();
        }
        long now=System.nanoTime();
        if (client.player.hurtTime>0) transitions.combat(now);
        if (client.gui.screen()!=null || client.isPaused()) {transitions.pause(now);return;}
        if (!active) {
            config.defaultRenderDistance=controller.current();
            config.restorePending=true;
            if (!config.save()) return;
            active=true;lastApplied=controller.current();transitions.pause(now);
        }
        if (world!=client.level) {
            world=client.level;resetAnalysis();performance.reset();lastFrame=0;
        }
        var player=client.player;
        if (transitions.observe(client.level,player.getX(),player.getY(),player.getZ(),player.getDeltaMovement().length(),
                player.hurtTime>0,now,config.enableTeleportRecovery,config.teleportThresholdBlocks,config.combatGraceMs)) {
            controller.apply(config.bound(2));lastApplied=controller.current();
            resetAnalysis();performance.reset();lastFrame=0;
        }
        double speed=player.getDeltaMovement().length()*20;
        boolean turning=Math.abs(player.getYRot()-lastYaw)>15;
        intervalMs=AnalysisPolicy.interval(speed,player.isFallFlying(),turning,
                config.enableHardwareAdaptive && (performance.cpuPressure() || analysisMs>3),
                config.stationaryIntervalMs,config.walkingIntervalMs,config.elytraIntervalMs,
                config.enableAdaptiveAnalysis,config.analysisIntervalMs);
        if (score==null || now-lastAnalysis>=intervalMs*1_000_000L) {
            long begin=System.nanoTime();
            var eye=player.getEyePosition();
            var key=new EnvironmentCache.Key((int)Math.floor(eye.x*4),(int)Math.floor(eye.y*4),
                    (int)Math.floor(eye.z*4),(int)Math.floor(player.getYRot()/5),(int)Math.floor(player.getXRot()/5));
            long revision=((EnvironmentRevision)client.level).dynamicchunkie$revision();
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
                    client.level.getMaxSectionY()-client.level.getMinSectionY()+1,config.cacheMemoryLimitMb));
        target=stableTarget.update(config.bound(candidate),now,config);
        int current=controller.current(),bounded=config.bound(current);
        if (current!=bounded) {controller.apply(bounded);lastApplied=controller.current();return;}
        if (Math.abs(target-current)<=config.deadZoneChunks) return;
        int next=transitions.next(current,target,now,config.transitionIntervalMs);
        if (next!=current) {controller.apply(config.bound(next));lastApplied=controller.current();}
    }
    private void resetAnalysis() {
        score=null;cache.clear();stableTarget.reset();smoothed=Double.NaN;lastAnalysis=0;
    }
    public void restore(Minecraft client) {
        if (!active) return;
        controller.apply(config.defaultRenderDistance);
        client.options.save();config.restorePending=false;config.save();
        active=false;world=null;target=0;resetAnalysis();transitions.reset();performance.reset();lastFrame=0;
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
