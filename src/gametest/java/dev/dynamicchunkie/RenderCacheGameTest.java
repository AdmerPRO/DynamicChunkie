package dev.dynamicchunkie;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.CompiledSection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

/** Checks object identity in a real renderer, rather than only checking option values. */
public final class RenderCacheGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var backupSettings = context.computeOnClient(client -> DynamicChunkie.config().copy());
        context.runOnClient(client -> { var c = backupSettings.copy(); c.enableTeleportRecovery=false; c.enableFpsAdaptive=false; c.enableHardwareAdaptive=false; DynamicChunkie.applyConfig(c); });
        try {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            new SodiumCacheGameTest().runTest(context);
            return;
        }
        context.runOnClient(client -> new RenderDistanceController(client).apply(4));
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> client.setScreen(new Screen(Component.literal("Render cache test")) {
                @Override public boolean isPauseScreen() { return false; }
            }));
            world.getClientWorld().waitForChunksRender();
            BlockPos position = context.computeOnClient(client -> client.player.blockPosition().below());
            context.waitFor(client -> {
                var section = section(area(client), position);
                return section != null && section.getCompiled() != CompiledSection.UNCOMPILED;
            }, 1200);
            var original = context.computeOnClient(client -> area(client));
            var section = context.computeOnClient(client -> section(original, position));
            var mesh = context.computeOnClient(client -> section.getCompiled());
            int simulation = context.computeOnClient(client -> client.options.simulationDistance().get());
            long reused = context.computeOnClient(client -> RenderCache.preservedChanges());

            for (int radius : new int[]{2, 8, 2, 4}) {
                context.runOnClient(client -> new RenderDistanceController(client).apply(radius));
                context.waitFor(client -> area(client).getViewDistance() == radius, 200);
                context.waitTicks(5);
                context.runOnClient(client -> {
                    require(area(client) == original, "ViewArea was recreated");
                    require(section(original, position) == section, "Near section was recreated");
                    require(section.getCompiled() == mesh, "Near geometry was rebuilt");
                    require(client.options.simulationDistance().get() == simulation, "Simulation Distance changed");
                });
            }
            context.runOnClient(client -> {
                require(RenderCache.preservedChanges() >= reused + 4, "Cache path did not handle every transition");
            });
            world.getServer().runOnServer(server -> server.overworld()
                    .setBlockAndUpdate(position, Blocks.GOLD_BLOCK.defaultBlockState()));
            context.waitFor(client -> section.getCompiled() != mesh &&
                    section.getCompiled() != CompiledSection.UNCOMPILED, 400);
            context.runOnClient(client -> {
                // Genuine resource/graphics reloads must still invalidate the cache.
                client.levelRenderer.allChanged();
            });
            context.waitFor(client -> area(client) != original, 200);
            context.runOnClient(client -> {
                client.setScreen(null);
                DynamicChunkie.LOG.info("Render cache regression passed: 2/8/2/4 preserved near meshes; block updates and full reload still work; simulation unchanged");
            });
        }
        } finally { context.runOnClient(client -> DynamicChunkie.applyConfig(backupSettings)); }
    }
    private static net.minecraft.client.renderer.ViewArea area(net.minecraft.client.Minecraft client) {
        return (net.minecraft.client.renderer.ViewArea)((RenderStorageAccess)client.levelRenderer).dynamicchunkie$viewArea();
    }
    private static net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection section(
            net.minecraft.client.renderer.ViewArea area, BlockPos position) {
        return (net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection)((CachedViewArea)area).dynamicchunkie$sectionAt(position);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
