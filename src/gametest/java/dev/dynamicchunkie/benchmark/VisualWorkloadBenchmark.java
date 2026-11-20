package dev.dynamicchunkie.benchmark;

import dev.dynamicchunkie.DynamicChunkie;
import dev.dynamicchunkie.runtime.AnalysisScheduler;
import dev.dynamicchunkie.runtime.WorkCoordinator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** Disposable integrated-server farms and a deterministic client-side particle emitter. */
public final class VisualWorkloadBenchmark implements FabricClientGameTest {
    private record Run(String scene, int entities, int limitPerType, int repeat, boolean enabled,
                       int ticks, long selectionCalls, long selectionNs, FrameCapture.Result sample, double[] frameMs) {}
    private record Report(long seed, boolean sodium, int fpsCap, int width, int height, ArrayList<Run> runs, ArrayList<SelectionComparison.Sample> selectorSamples) {}

    @Override public void runTest(ClientGameTestContext context) {
        var backup = context.computeOnClient(client -> DynamicChunkie.config().copy());
        int cap = Integer.getInteger("dynamicchunkie.benchmarkFpsCap", 120);
        int repeats = Integer.getInteger("dynamicchunkie.benchmarkRepeats", 6);
        int ticks = Integer.getInteger("dynamicchunkie.benchmarkSampleTicks", 200);
        int warmup = Integer.getInteger("dynamicchunkie.benchmarkWarmupTicks", 100);
        if (repeats < 1 || repeats > 20 || ticks < 20 || ticks > 10000 || warmup < 10 || warmup > 10000)
            throw new IllegalArgumentException("Invalid workload benchmark length");
        long seed = Long.getLong("dynamicchunkie.benchmarkSeed", 263L);
        var random = new Random(seed);
        var selectorSamples = new ArrayList<SelectionComparison.Sample>();
        var runs = new ArrayList<Run>();
        int fps = context.computeOnClient(c -> c.options.framerateLimit().get());
        int width = context.computeOnClient(c -> c.getWindow().getScreenWidth());
        int height = context.computeOnClient(c -> c.getWindow().getScreenHeight());
        int requestedWidth = Integer.getInteger("dynamicchunkie.benchmarkWidth",width);
        int requestedHeight = Integer.getInteger("dynamicchunkie.benchmarkHeight",height);
        var idle = context.computeOnClient(c -> c.options.inactivityFpsLimit().get());
        boolean vsync = context.computeOnClient(c -> c.options.enableVsync().get());
        Report report;
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(c -> {
                c.getWindow().setWindowed(requestedWidth,requestedHeight);
                c.options.framerateLimit().set(cap);
                c.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
                c.options.enableVsync().set(false);
            });
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("tp @a 0 100 0 -90 0");
            world.getServer().runCommand("fill -4 99 -12 36 99 12 minecraft:stone");
            int previous = 0;
            for (int count : new int[]{0, 100, 1000, 5000}) {
                int start = previous;
                world.getServer().runOnServer(server -> {
                    var level = server.overworld();
                    for (int i = start; i < count; i++) {
                        Mob mob = (i % 2 == 0 ? EntityTypes.COW : EntityTypes.PIG).create(level, EntitySpawnReason.COMMAND);
                        if (mob == null) throw new AssertionError("Cannot create benchmark mob");
                        mob.setNoAi(true);
                        mob.setNoGravity(true);
                        mob.setPermanentlyInvulnerable(true);
                        mob.setPersistenceRequired();
                        mob.snapTo(8 + i % 20, 100 + i / 400 * 0.25, i / 20 % 20 - 10);
                        if (!level.addFreshEntity(mob)) throw new AssertionError("Cannot add benchmark mob");
                    }
                });
                previous = count;
                if (count > 0) context.waitFor(client -> {
                    int found = 0;
                    for (var entity : client.level.entitiesForRendering())
                        if (entity.getType() == EntityTypes.COW || entity.getType() == EntityTypes.PIG) found++;
                    return found >= count;
                }, 800);
                for (int limit : count == 0 ? new int[]{0} : new int[]{10, 100}) {
                    if (count > 0) selectorSamples.addAll(context.computeOnClient(c -> SelectionComparison.measure(c,limit,seed)));
                    for (int repeat = 0; repeat < repeats; repeat++) {
                        boolean first = random.nextBoolean();
                        for (int order = 0; order < 2; order++) {
                            boolean on = order == 0 ? first : !first;
                            context.runOnClient(client -> {
                                var c = backup.copy();
                                c.enabled = true;
                                c.minRenderDistance = c.maxRenderDistance = 8;
                                c.enableHotSoftSections = c.enableTerrainSectionCulling = c.enableEntityCulling = false;
                                c.enableAdaptiveEntityDistance = c.enableAdaptiveModuleBudgets = false;
                                c.profilerOverlay = c.debugMode = c.enableSmartClouds = false;
                                c.enableEntityTypeLimits = count > 0 && on;
                                c.entityTypeLimits.clear();
                                c.entityTypeLimits.put("minecraft:cow",limit);
                                c.entityTypeLimits.put("minecraft:pig",limit);
                                c.enableAdaptiveParticles = count == 0 && on;
                                c.particleSpawnBudget = 48;
                                if (!DynamicChunkie.applyConfig(c)) throw new AssertionError("Cannot apply workload settings");
                                client.particleEngine.clearParticles();
                            });
                            for (int i = 0; i < warmup; i++) step(context, count, i);
                            var before = context.computeOnClient(client -> selection());
                            context.runOnClient(client -> FrameCapture.begin());
                            for (int i = 0; i < ticks; i++) step(context, count, i);
                            var sample = context.computeOnClient(client -> FrameCapture.end());
                            var after = context.computeOnClient(client -> selection());
                            long selected = after.calls() - before.calls();
                            if (sample.frames < 10 || count > 0 && on && selected == 0)
                                throw new AssertionError("Insufficient frame or selector samples");
                            runs.add(new Run(count == 0 ? "particles" : "farm", count, limit, repeat, on, ticks,
                                    selected, after.totalNs() - before.totalNs(), sample, sample.intervals));
                        }
                    }
                }
            }
            report = context.computeOnClient(c -> new Report(seed, dev.dynamicchunkie.RenderCache.sodiumPresent(), cap,
                    c.getWindow().getWidth(), c.getWindow().getHeight(), runs, selectorSamples));
        } finally {
            context.runOnClient(c -> {
                FrameCapture.cancel();
                c.getWindow().setWindowed(width,height);
                c.options.framerateLimit().set(fps);
                c.options.enableVsync().set(vsync);
                c.options.inactivityFpsLimit().set(idle);
                DynamicChunkie.applyConfig(backup);
            });
        }
        var directory = Path.of(System.getProperty("dynamicchunkie.benchmarkOutput"));
        try {
            Files.createDirectories(directory);
        Files.writeString(directory.resolve("workloads-" + System.currentTimeMillis() + ".json"),
                new com.google.gson.Gson().toJson(report));
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private static AnalysisScheduler.Sample selection() {
        return WorkCoordinator.snapshot().get(AnalysisScheduler.Module.ENTITY_SELECTION.ordinal());
    }

    private static void step(ClientGameTestContext context, int count, int tick) {
        if (count == 0) context.runOnClient(c -> {
            for (int i = 0; i < 256; i++)
                c.particleEngine.createParticle(i % 16 == 0 ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.SMOKE,
                        6 + (i % 8), 101 + ((i + tick) % 5) * .2, (i / 8 % 8) - 4, 0, .02, 0);
        });
        context.waitTick();
    }
}
