package dev.dynamicchunkie;
import dev.dynamicchunkie.sections.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
/** Tests real renderer membership and native rebuild admission, with both renderer backends. */
public final class HotSoftGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var backup=context.computeOnClient(client->DynamicChunkie.config().copy());
        int simulation=context.computeOnClient(client->client.options.simulationDistance().get());
        try(var world=context.worldBuilder().create()) {
            context.runOnClient(client->{
                var c=backup.copy();c.enabled=true;c.minRenderDistance=8;c.maxRenderDistance=8;
                c.enableColdGeometryEviction = RenderCache.sodiumSupported(); c.coldGeometryGraceMs=1000;
                c.enableHotSoftSections=true;c.hotSoftScanBudgetMicros=10000;c.enableTerrainSectionCulling=false;
                c.enableTeleportRecovery=false;c.enableFpsAdaptive=false;c.enableHardwareAdaptive=false;
                if(!DynamicChunkie.applyConfig(c))throw new AssertionError("Cannot apply section settings");
            });
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill -48 96 0 -33 111 15 minecraft:gold_block");
            world.getServer().runCommand("fill 48 96 0 63 111 15 minecraft:stone");
            world.getServer().runCommand("tp @a 0.5 104 8.5 90 0");
            context.waitTicks(5);
            context.runOnClient(client->{client.player.getAbilities().flying=true;client.player.onUpdateAbilities();});
            world.getConnection().waitForChunksRender(false);
            context.waitFor(client->HotSoftSections.active() && HotSoftSections.state(-3,6,0)==HotSoftGrid.State.HOT,400);
            context.runOnClient(client->{
                if(HotSoftSections.state(-4,7,1)!=HotSoftGrid.State.SOFT)
                    throw new AssertionError("Diagonal neighbour missing from soft halo");
                if(HotSoftSections.defer(-3,6,0,false))throw new AssertionError("Visible rebuild deferred");
            });
            world.getServer().runCommand("tp @a 0.5 104 8.5 -90 0");
            context.waitFor(client->HotSoftSections.active() && HotSoftSections.state(-3,6,0)==HotSoftGrid.State.COLD,400);
            context.runOnClient(client->{
                if(!HotSoftSections.defer(-3,6,0,false))throw new AssertionError("Built cold geometry was not deferred");
                if(HotSoftSections.pending()==0)throw new AssertionError("Deferred queue empty");
                if(HotSoftSections.defer(-3,6,0,true))throw new AssertionError("Urgent rebuild was deferred");
                if(!HotSoftSections.defer(-3,6,0,false))throw new AssertionError("Cold geometry could not be queued again");
                if(HotSoftSections.defer(0,20,0,false))throw new AssertionError("Initial / missing mesh build deferred");
            });
            if (RenderCache.sodiumSupported()) {
                context.waitFor(client -> HotSoftSections.isDeferred(-3,6,0) &&
                        !dev.dynamicchunkie.render.SectionBackends.current().hasBuiltMesh(-3,6,0),800);
                if (context.computeOnClient(client -> HotSoftSections.evictions()) == 0)
                    throw new AssertionError("Native cold allocation was not retired");
            }
            world.getServer().runCommand("tp @a 0.5 104 8.5 90 0");
            var polls=new java.util.concurrent.atomic.AtomicInteger();
            context.waitFor(client->{
                if(polls.incrementAndGet()%100==0)DynamicChunkie.LOG.info("Hot replay state={} pending={} keyPending={} replays={} active={}",
                        HotSoftSections.state(-3,6,0),HotSoftSections.pending(),HotSoftSections.isDeferred(-3,6,0),HotSoftSections.replayedTotal(),HotSoftSections.active());
                return HotSoftSections.state(-3,6,0)==HotSoftGrid.State.HOT && !HotSoftSections.isDeferred(-3,6,0);
            },400);
            world.getConnection().waitForChunksRender(false);
            context.waitFor(client -> dev.dynamicchunkie.render.SectionBackends.current().hasBuiltMesh(-3,6,0),400);
            context.runOnClient(client->{
                if(!HotSoftSections.drawAllowed(-48,96,0))throw new AssertionError("Revisited hot section hidden");
                var c=DynamicChunkie.config().copy();
                c.filterHotSectionDraws=false;
                c.enableColdGeometryEviction=false;
                DynamicChunkie.applyConfig(c);
            });
            context.waitFor(client->HotSoftSections.active() && HotSoftSections.hotCount()==0,200);
            context.runOnClient(client->{
                if (!HotSoftSections.drawAllowed(48,96,0) || !HotSoftSections.defer(3,6,0,false))
                    throw new AssertionError("Queue-only mode must retain an offscreen rebuild and admit drawing");
                var c=DynamicChunkie.config().copy();
                c.deferColdSectionRebuilds=false;
                c.filterHotSectionDraws=true;
                DynamicChunkie.applyConfig(c);
            });
            context.waitFor(client->HotSoftSections.filtering() && !HotSoftSections.isDeferred(3,6,0),200);
            context.runOnClient(client->{
                if (HotSoftSections.defer(3,6,0,false))
                    throw new AssertionError("Draw-only mode deferred a rebuild");
                var c=DynamicChunkie.config().copy();c.enableHotSoftSections=false;DynamicChunkie.applyConfig(c);
            });
            context.waitFor(client->!HotSoftSections.active(),100);
            context.runOnClient(client->{
                if(!HotSoftSections.drawAllowed(-48,96,0))throw new AssertionError("Disabled filter still hides geometry");
                if(client.options.simulationDistance().get()!=simulation)throw new AssertionError("Simulation Distance changed");
            });
            DynamicChunkie.LOG.info("Hot/soft regression passed: diagonal halo, camera rotation, cold deferral, urgent/initial safety, warm replay and disable");
        }finally {context.runOnClient(client->DynamicChunkie.applyConfig(backup));}
    }
}
