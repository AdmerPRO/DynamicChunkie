package dev.dynamicchunkie;

import dev.dynamicchunkie.compat.SodiumDistanceAccess;
import dev.dynamicchunkie.mixin.sodium.SodiumRendererAccessor;
import dev.dynamicchunkie.compat.DynamicChunkieModMenu;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;

public final class SodiumCacheGameTest implements FabricClientGameTest {
    private static RenderSectionManager manager() {
        return ((SodiumRendererAccessor)(Object)SodiumWorldRenderer.instance()).dynamicchunkie$getManager();
    }
    private static RenderSection section(SectionPos pos) {
        return (RenderSection)((SodiumDistanceAccess)(Object)manager())
                .dynamicchunkie$sectionAt(pos.x(), pos.y(), pos.z());
    }
    @Override public void runTest(ClientGameTestContext context) {
        var backupSettings = context.computeOnClient(client -> DynamicChunkie.config().copy());
        context.runOnClient(client -> { var c = backupSettings.copy(); c.enableTeleportRecovery=false; c.enableFpsAdaptive=false; c.enableHardwareAdaptive=false; DynamicChunkie.applyConfig(c); });
        try {
        testMenu(context);
        context.runOnClient(client -> new RenderDistanceController(client).apply(4));
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> client.setScreen(new Screen(Component.literal("Sodium cache test")) {
                @Override public boolean isPauseScreen() { return false; }
            }));
            world.getClientWorld().waitForChunksRender();
            var ground = context.computeOnClient(client -> client.player.blockPosition().below());
            var position = SectionPos.of(ground);
            context.waitFor(client -> section(position) != null && section(position).isBuilt(), 1200);
            var originalManager = context.computeOnClient(client -> manager());
            var originalSection = context.computeOnClient(client -> section(position));
            var visibility = context.computeOnClient(client -> originalSection.getVisibilityData());
            var storage = context.computeOnClient(client -> originalSection.getRegion().getStorage(DefaultTerrainRenderPasses.SOLID));
            int simulation = context.computeOnClient(client -> client.options.simulationDistance().get());
            long reused = context.computeOnClient(client -> RenderCache.preservedChanges());
            for (int radius : new int[]{2, 8, 2, 4}) {
                context.runOnClient(client -> new RenderDistanceController(client).apply(radius));
                long previous = reused++;
                context.waitFor(client -> RenderCache.preservedChanges() > previous, 200);
                for (int frame = 0; frame < 8; frame++) {
                    context.waitTick();
                    context.runOnClient(client -> {
                        require(manager() == originalManager, "Sodium manager was destroyed");
                        require(section(position) == originalSection, "Near Sodium section was replaced");
                        require(!originalSection.isDisposed() && originalSection.isBuilt(), "Near section disappeared");
                        require(originalSection.getVisibilityData() == visibility, "Near mesh was rebuilt");
                        require(originalSection.getRegion().getStorage(DefaultTerrainRenderPasses.SOLID) == storage,
                                "Near mesh storage was replaced");
                        require(SodiumWorldRenderer.instance().getVisibleChunkCount() > 0, "Blank terrain frame");
                        require(client.options.simulationDistance().get() == simulation, "Simulation Distance changed");
                    });
                }
            }
            long meshBytes = context.computeOnClient(client -> originalSection.getLastMeshResultSize());
            world.getServer().runOnServer(server -> server.overworld()
                    .setBlockAndUpdate(ground, Blocks.AIR.defaultBlockState()));
            context.waitFor(client -> originalSection.getLastMeshResultSize() != meshBytes, 400);
            context.runOnClient(client -> client.levelRenderer.allChanged());
            context.waitFor(client -> manager() != originalManager, 200);
            testAbruptTargets(context);
            context.runOnClient(client -> {
                client.setScreen(null);
                DynamicChunkie.LOG.info("Sodium beta.1 regression passed: manager/near mesh retained across 2/8/2/4; no empty terrain frames; block updates and full reload work; simulation unchanged");
            });
        }
        } finally { context.runOnClient(client -> DynamicChunkie.applyConfig(backupSettings)); }
    }
    private void testAbruptTargets(ClientGameTestContext context) {
        var backup = context.computeOnClient(client -> DynamicChunkie.config().copy());
        try {
            context.runOnClient(client -> {
                new RenderDistanceController(client).apply(16);
                setUniformTarget(2);
                client.setScreen(null);
            });
            context.waitFor(client -> {
                int distance = client.options.renderDistance().get();
                if (distance == 16) return false;
                require(distance == 2, "Decrease used intermediate distance: " + distance);
                return true;
            }, 400);
            context.runOnClient(client -> setUniformTarget(16));
            context.waitFor(client -> {
                int distance = client.options.renderDistance().get();
                if (distance == 2) return false;
                require(distance >= 2 && distance <= 16, "Increase left configured range: " + distance);
                return distance == 16;
            }, 400);
            DynamicChunkie.LOG.info("Transition regression passed: immediate decrease to 2, bounded growth back to 16");
        } finally {
            context.runOnClient(client -> DynamicChunkie.applyConfig(backup));
        }
    }
    private void setUniformTarget(int value) {
        var settings = DynamicChunkie.config().copy();
        settings.minRenderDistance = 2;
        settings.maxRenderDistance = 32;
        settings.tunnelDistance = settings.smallCaveDistance = settings.largeCaveDistance =
                settings.undergroundOpenDistance = settings.surfaceDistance =
                settings.mountainDistance = settings.highAltitudeDistance = value;
        settings.smoothingFactor = 1;
        settings.targetStabilityMs = 0;
        settings.deadZoneChunks = 0;
        settings.transitionIntervalMs = 500;
        require(DynamicChunkie.applyConfig(settings), "Cannot apply test profile");
    }
    private void testMenu(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> DynamicChunkie.config().copy());
        require(net.fabricmc.loader.api.FabricLoader.getInstance()
                .getEntrypoints("modmenu", com.terraformersmc.modmenu.api.ModMenuApi.class).stream()
                .anyMatch(api -> api instanceof DynamicChunkieModMenu), "Mod Menu entrypoint missing");
        context.setScreen(() -> new DynamicChunkieModMenu().getModConfigScreenFactory().create(null));
        context.clickScreenButton(">");
        context.runOnClient(client -> {
            var fields = client.screen.children().stream().filter(w -> w instanceof EditBox)
                    .map(w -> (EditBox)w).toList();
            fields.get(0).setValue("6");
            fields.get(1).setValue("12");
        });
        context.clickScreenButton("Save");
        context.runOnClient(client -> {
            require(DynamicChunkie.config().minRenderDistance == 6 && DynamicChunkie.config().maxRenderDistance == 12,
                    "Menu values were not applied");
            require(DynamicChunkie.applyConfig(original), "Could not restore test settings");
            DynamicChunkie.LOG.info("Mod Menu regression passed: provider discovered, settings screen opened and values saved");
        });
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
