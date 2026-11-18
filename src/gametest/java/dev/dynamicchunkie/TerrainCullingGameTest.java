package dev.dynamicchunkie;
import dev.dynamicchunkie.terrain.TerrainCulling;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** A full section casts a shadow; removing it must reveal cached draw input again. */
public final class TerrainCullingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var backup=context.computeOnClient(client->DynamicChunkie.config().copy());
        boolean smart=context.computeOnClient(client->client.smartCull);
        try(var world=context.worldBuilder().create()) {
            context.runOnClient(client->{
                var c=backup.copy();c.enabled=true;c.minRenderDistance=8;c.maxRenderDistance=8;
                c.enableTerrainSectionCulling=true;c.terrainChecksPerFrame=1024;c.enableTeleportRecovery=false;
                c.enableFpsAdaptive=false;c.enableHardwareAdaptive=false;
                if(!DynamicChunkie.applyConfig(c))throw new AssertionError("Cannot apply terrain settings");
                // Isolate the new culler from the native connectivity graph in both renderers.
                client.smartCull=false;
            });
            world.getServer().runCommand("setblock 0 103 8 minecraft:stone");
            world.getServer().runCommand("fill 16 96 0 31 111 15 minecraft:stone");
            world.getServer().runCommand("fill 64 96 0 79 111 15 minecraft:gold_block");
            world.getServer().runCommand("tp @a 0.5 104 8.5 -90 0");
            world.getConnection().waitForChunksRender(false);
            var polls=new java.util.concurrent.atomic.AtomicInteger();
            context.waitFor(client->{
                if(polls.incrementAndGet()%100==0)DynamicChunkie.LOG.info("Terrain discovery: {}",TerrainCulling.status());
                return TerrainCulling.occluders()>0;
            },600);
            context.runOnClient(client->{
                if(!TerrainCulling.hide(64,96,0))throw new AssertionError("Full opaque section did not occlude target");
                if(TerrainCulling.hide(0,96,0))throw new AssertionError("Camera section was culled");
            });
            context.waitTicks(10);
            var draws=new java.util.concurrent.atomic.AtomicInteger();
            context.waitFor(client->{
                if(draws.incrementAndGet()%100==0)DynamicChunkie.LOG.info("Terrain draw: {} hooks={} skipped={} smartCull={} nativeVisible={}",
                        TerrainCulling.status(),TerrainCulling.hooks(),TerrainCulling.skipped(),client.smartCull,
                        client.levelExtractor.countRenderedSections());
                return TerrainCulling.hooks()>0 && TerrainCulling.skipped()>0;
            },400);
            context.runOnClient(client->{
                if(TerrainCulling.candidates()<TerrainCulling.skipped() || TerrainCulling.candidates()==0)
                    throw new AssertionError("Draw-candidate telemetry is inconsistent");
            });
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
                context.runOnClient(client->{
                    var c=DynamicChunkie.config().copy();c.preserveRenderCache=false;DynamicChunkie.applyConfig(c);
                });
                context.waitFor(client->TerrainCulling.occluders()>0 && TerrainCulling.skipped()>0,600);
            }
            // Keep the camera stationary so Sodium command-batch invalidation is exercised.
            world.getServer().runCommand("fill 16 96 0 31 111 15 minecraft:air");
            context.waitFor(client->client.level.getBlockState(new net.minecraft.core.BlockPos(16,100,8)).isAir(),200);
            context.runOnClient(client->{
                if(TerrainCulling.hide(64,96,0))throw new AssertionError("Stale occlusion survived block removal");
            });
            context.waitFor(client->TerrainCulling.skipped()==0,200);
            world.getServer().runCommand("fill 16 96 0 31 111 15 minecraft:glass");
            world.getConnection().waitForChunksRender(false);context.waitTicks(40);
            context.runOnClient(client->{
                if(TerrainCulling.hide(64,96,0))throw new AssertionError("Glass section was treated as opaque");
                var c=DynamicChunkie.config().copy();c.enableTerrainSectionCulling=false;DynamicChunkie.applyConfig(c);
                if(TerrainCulling.hide(64,96,0))throw new AssertionError("Disabled culler still hides terrain");
            });
            DynamicChunkie.LOG.info("Terrain regression passed: real draw skips, full-section shadow, camera safety, stationary block removal and transparent blocks");
        }finally {context.runOnClient(client->{client.smartCull=smart;DynamicChunkie.applyConfig(backup);TerrainCulling.invalidate();});}
    }
}
