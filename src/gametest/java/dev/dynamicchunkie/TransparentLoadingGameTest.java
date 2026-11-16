package dev.dynamicchunkie;

import dev.dynamicchunkie.loading.LoadingPresentation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.world.phys.BlockHitResult;

/** Exercises actual input and native reload completion, rather than removing the overlay. */
public final class TransparentLoadingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var backup=context.computeOnClient(client -> DynamicChunkie.config().copy());
        var completion=new CompletableFuture<Void>();
        var callbacks=new AtomicInteger();
        try (var world=context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill -8 99 -8 8 99 8 minecraft:stone");
            world.getServer().runCommand("tp @a 0.5 100 0.5 -90 0");
            world.getClientLevel().waitForChunksRender();
            context.runOnClient(client -> {
                var settings=backup.copy();settings.enabled=true;settings.simpleLoadingScreens=true;settings.loadingInputPassthrough=true;
                DynamicChunkie.applyConfig(settings);
                client.setScreen(null);
                client.setOverlay(new LoadingOverlay(client,new ReloadInstance() {
                    public CompletableFuture<?> done() {return completion;}
                    public float getActualProgress() {return .75f;}
                }, failure -> {if (failure.isPresent()) throw new AssertionError(failure.get());callbacks.incrementAndGet();},false));
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                require(client.getOverlay() instanceof LoadingOverlay,"Native overlay was removed prematurely");
                require(!client.getOverlay().isPauseScreen(),"Reload still pauses active gameplay");
                LoadingPresentation.beginInput();
                try {require(client.getOverlay()==null,"Input is still blocked by reload overlay");}
                finally {LoadingPresentation.endInput();}
            });
            double before=context.computeOnClient(client -> client.player.getX());
            context.getInput().holdKeyFor(options -> options.keyUp,20);
            context.runOnClient(client -> require(client.player.getX()>before+.2,"Movement was blocked during reload"));
            world.getServer().runCommand("tp @a 0.5 100 0.5 -90 0");
            BlockPos block=new BlockPos(2,101,0);
            world.getServer().runCommand("setblock 2 101 0 minecraft:stone");
            context.waitFor(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(block),200);
            context.getInput().holdKeyFor(options -> options.keyAttack,5);
            context.waitFor(client -> client.level.getBlockState(block).isAir(),200);
            context.runOnClient(client -> {
                var enabled=DynamicChunkie.config().copy();var disabled=enabled.copy();disabled.enabled=false;
                DynamicChunkie.applyConfig(disabled);
                require(!LoadingPresentation.simple() && !LoadingPresentation.readyForInput(),"Disabled mod still changes loading policy");
                DynamicChunkie.applyConfig(enabled);
            });
            completion.complete(null);
            context.waitFor(client -> client.getOverlay()==null && callbacks.get()==1,200);
            DynamicChunkie.LOG.info("Transparent reload regression passed: movement, block breaking and native completion callback");
        } finally {
            completion.complete(null);
            context.runOnClient(client -> DynamicChunkie.applyConfig(backup));
        }
    }
    private static void require(boolean value,String message) {if (!value) throw new AssertionError(message);}
}
