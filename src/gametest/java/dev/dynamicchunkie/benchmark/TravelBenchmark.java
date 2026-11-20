package dev.dynamicchunkie.benchmark;

import dev.dynamicchunkie.DynamicChunkie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Fixed flat-world route, repeated on fresh lanes to separate generation from streaming. */
public final class TravelBenchmark implements FabricClientGameTest {
    private record Run(boolean pregenerated, int repeat, FrameCapture.Result sample, double[] intervals) {}

    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> DynamicChunkie.config().copy());
        var runs = new ArrayList<Run>();
        int repeats = Integer.getInteger("dynamicchunkie.benchmarkRepeats",6);
        if (repeats < 1 || repeats > 10) throw new IllegalArgumentException("Invalid travel repeats");
        var order = new Random(Long.getLong("dynamicchunkie.benchmarkSeed",263L));
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var c = original.copy();
                c.enabled = true;
                c.minRenderDistance = c.maxRenderDistance = 4;
                if (!DynamicChunkie.applyConfig(c)) throw new AssertionError("Cannot apply travel profile");
            });
            world.getServer().runCommand("gamemode spectator @a");
            for (int repeat = 0; repeat < repeats; repeat++) {
                boolean first = order.nextBoolean();
                for (int index = 0; index < 2; index++) {
                    boolean pregenerated = index == 0 ? first : !first;
                    int lane = 2048 + (repeat * 2 + index) * 1024;
                    if (pregenerated) {
                        // Visit each checkpoint using the same native view distance, then leave the route.
                        for (int step = 0; step < 6; step++) move(world, context, lane, step);
                        move(world, context, 0, 0);
                        context.waitFor(c -> !c.level.getChunkSource().hasChunk(lane >> 4, 0), 400);
                    }
                    context.runOnClient(client -> FrameCapture.begin());
                    for (int step = 0; step < 6; step++) move(world, context, lane, step);
                    var sample = context.computeOnClient(client -> FrameCapture.end());
                    if (sample.frames == 0) throw new AssertionError("Empty travel sample");
                    runs.add(new Run(pregenerated,repeat,sample,sample.intervals));
                }
            }
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("item replace entity @a armor.chest with minecraft:elytra");
            world.getServer().runCommand("tp @a 0 260 0 -90 12");
            context.waitFor(c -> c.player.getY() > 220 && !c.player.onGround(), 200);
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().startFallFlying());
            context.waitFor(c -> c.player.isFallFlying(), 200);
            context.waitTicks(10);
            context.runOnClient(c -> {
                if (!c.player.isFallFlying()) throw new AssertionError("Elytra flight did not persist");
            });
            var reload = context.computeOnClient(client -> client.reloadResourcePacks());
            context.waitFor(c -> reload.isDone(),600);
            reload.join();
        } finally {
            context.runOnClient(c -> { FrameCapture.cancel(); DynamicChunkie.applyConfig(original); });
        }
        try {
            var directory = Path.of(System.getProperty("dynamicchunkie.benchmarkOutput"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("travel-" + System.currentTimeMillis() + ".json"),
                    new com.google.gson.Gson().toJson(runs));
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private static void move(TestSingleplayerContext world, ClientGameTestContext context, int lane, int step) {
        int x = lane + step * 32;
        world.getServer().runCommand("tp @a " + x + " 120 0 -90 0");
        context.waitFor(c -> Math.abs(c.player.getX() - x) < 1 && c.level.getChunkSource().hasChunk(x >> 4,0), 400);
        world.getConnection().waitForChunksRender(false);
        context.waitTicks(4);
    }
}
