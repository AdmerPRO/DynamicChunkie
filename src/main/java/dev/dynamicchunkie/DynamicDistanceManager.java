package dev.dynamicchunkie;

import net.minecraft.client.Minecraft;

public final class DynamicDistanceManager {
    final DynamicChunkieConfig config;
    private final EnvironmentAnalyzer analyzer = new EnvironmentAnalyzer();
    private RenderDistanceController controller;
    private boolean active, initialized;
    private Object world;
    private long lastAnalysis, lastTransition;
    private double smoothed = Double.NaN, credit;
    private int lastApplied;
    private EnvironmentScore score;
    private int target;

    public DynamicDistanceManager(DynamicChunkieConfig config) { this.config = config; }
    public void tick(Minecraft client) {
        if (!initialized) {
            controller = new RenderDistanceController(client);
            config.minRenderDistance = Math.min(config.minRenderDistance, controller.supportedMaximum());
            config.maxRenderDistance = Math.min(config.maxRenderDistance, controller.supportedMaximum());
            config.validate();
            if (config.restorePending && config.defaultRenderDistance >= 2) {
                controller.apply(config.defaultRenderDistance);
                client.options.save();
                config.restorePending = false;
            }
            if (config.defaultRenderDistance < 2) config.defaultRenderDistance = controller.current();
            config.save();
            initialized = true;
        }
        if (!config.enabled || client.level == null || client.player == null) { restore(client); return; }
        // Respect manual video-setting changes. Never fight the options screen.
        if (active && controller.current() != lastApplied) {
            config.defaultRenderDistance = controller.current();
            config.save();
            lastApplied = controller.current();
        }
        if (client.gui.screen() != null || client.isPaused()) {
            lastTransition = System.nanoTime();
            return;
        }
        long now = System.nanoTime();
        if (!active) {
            config.defaultRenderDistance = controller.current();
            config.restorePending = true;
            if (!config.save()) return; // Do not change RD without a durable restore point.
            active = true;
            lastApplied = controller.current();
            lastTransition = now;
        }
        if (world != client.level) {
            world = client.level;
            smoothed = Double.NaN;
            lastAnalysis = 0;
            credit = 0;
        }
        long interval = config.analysisIntervalMs;
        if (config.enableElytraOptimization && client.player.isFallFlying()) interval = Math.max(250, interval / 2);
        if (now - lastAnalysis >= interval * 1_000_000L) {
            score = analyzer.analyze(client, config);
            smoothed = Double.isNaN(smoothed) ? score.openness() :
                    smoothed + config.smoothingFactor * (score.openness() - smoothed);
            int candidate = EnvironmentAnalyzer.target(score, smoothed, config);
            if (Math.abs(candidate - target) > config.deadZoneChunks || target < config.minRenderDistance ||
                    target > config.maxRenderDistance) target = candidate;
            lastAnalysis = now;
        }
        double seconds = (now - lastTransition) / 1e9;
        if (seconds * 1000 < config.transitionIntervalMs) return;
        lastTransition = now;
        int current = controller.current();
        int bounded = config.bound(current);
        if (current != bounded) { controller.apply(bounded); lastApplied = controller.current(); return; }
        if (Math.abs(target - current) <= config.deadZoneChunks) { credit = 0; return; }
        credit = Math.min(8, credit + Math.min(seconds, 2) * config.transitionSpeed);
        int step = Math.min(Math.abs(target - current), (int)credit);
        if (step > 0) {
            controller.apply(config.bound(current + Integer.signum(target - current) * step));
            lastApplied = controller.current();
            credit -= step;
        }
    }
    public void restore(Minecraft client) {
        if (!active) return;
        controller.apply(config.defaultRenderDistance);
        client.options.save();
        config.restorePending = false;
        config.save();
        active = false;
        world = null;
        score = null;
        target = 0;
    }
    public void toggle(Minecraft client) {
        config.enabled = !config.enabled;
        if (!config.enabled) restore(client);
        config.save();
    }
    public int target() { return target; }
    public EnvironmentScore score() { return score; }
    public long analysisAgeMs() { return (System.nanoTime() - lastAnalysis) / 1_000_000; }
}
