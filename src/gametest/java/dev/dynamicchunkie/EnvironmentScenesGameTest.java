package dev.dynamicchunkie;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Sample the same camera direction, standing in a covered passage, a chamber and open sky. */
public final class EnvironmentScenesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0.5 180 0.5 0 0");
            context.waitFor(client->Math.abs(client.player.getY()-180)<.1,200);
            world.getConnection().waitForChunksRender(false);
            server.runCommand("fill -2 179 -16 2 183 16 minecraft:stone");
            server.runCommand("fill 0 180 -15 0 181 15 minecraft:air");
            var tunnel=measure(context,world,false);
            server.runCommand("fill -2 179 -16 2 183 16 minecraft:air");
            server.runCommand("fill -12 186 -12 12 186 12 minecraft:stone");
            server.runCommand("fill -12 179 -12 12 179 12 minecraft:stone");
            server.runCommand("fill -12 180 -12 -12 185 12 minecraft:stone");
            server.runCommand("fill 12 180 -12 12 185 12 minecraft:stone");
            server.runCommand("fill -12 180 -12 12 185 -12 minecraft:stone");
            server.runCommand("fill -12 180 12 12 185 12 minecraft:stone");
            context.waitFor(c->!c.level.getBlockState(new net.minecraft.core.BlockPos(12,182,0)).isAir(),200);
            var chamber=measure(context,world,false);
            server.runCommand("fill -12 180 -12 12 186 12 minecraft:air");
            var summit=measure(context,world,true);
            if (!(tunnel.openness()<chamber.openness() && chamber.openness()<summit.openness()))
                throw new AssertionError("Expected tunnel < chamber < summit at identical altitude");
            var settings=RuntimeSettings.capture(new DynamicChunkieConfig());
            int tunnelDistance=EnvironmentAnalyzer.target(tunnel,tunnel.openness(),settings);
            int chamberDistance=EnvironmentAnalyzer.target(chamber,chamber.openness(),settings);
            int summitDistance=EnvironmentAnalyzer.target(summit,summit.openness(),settings);
            if (tunnelDistance!=2 || chamberDistance<8 || chamberDistance>12 || summitDistance!=16)
                throw new AssertionError("Calibrated distances: "+tunnelDistance+", "+chamberDistance+", "+summitDistance);
            DynamicChunkie.LOG.info("Environment calibration passed at Y=180: tunnel={}, chamber={}, summit={}",
                    tunnel.openness(),chamber.openness(),summit.openness());
        }
    }

    private EnvironmentScore measure(ClientGameTestContext context, TestSingleplayerContext world, boolean sky) {
        context.waitFor(c->c.level.canSeeSky(c.player.blockPosition())==sky,200);
        world.getConnection().waitForChunksRender(false);
        return context.computeOnClient(c->new EnvironmentAnalyzer().analyze(c,new DynamicChunkieConfig()));
    }
}
