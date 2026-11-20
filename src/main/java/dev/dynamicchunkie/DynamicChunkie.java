package dev.dynamicchunkie;

import dev.dynamicchunkie.runtime.WorkCoordinator;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.Nullable;

public final class DynamicChunkie implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("DynamicChunkie");
    private static DynamicDistanceManager manager;
    private static RuntimeSettings settings;
    public static RuntimeSettings settings(){return settings;}
    public static void publishSettings(){settings=RuntimeSettings.capture(config());}
    private static dev.dynamicchunkie.culling.EntityCulling culling;
    public static dev.dynamicchunkie.culling.EntityCulling entityCulling() { return culling; }
    public static void onFrame() {
        dev.dynamicchunkie.benchmark.FrameCapture.frame();
        dev.dynamicchunkie.visual.VisualProfiler.frame();
        if (manager!=null) manager.onFrame(net.minecraft.client.Minecraft.getInstance());
    }
    public static DynamicDistanceManager distanceManager() {return manager;}
    public static @Nullable DynamicChunkieConfig configOrNull() {return manager==null?null:manager.config;}
    public static DynamicChunkieConfig config() { return manager.config; }
    public static boolean applyConfig(DynamicChunkieConfig value) {
        return manager.applyConfig(net.minecraft.client.Minecraft.getInstance(), value);
    }
    @Override public void onInitializeClient() {
        var config = DynamicChunkieConfig.load();
        RenderCache.configure(config);
        manager = new DynamicDistanceManager(config);
        publishSettings();
        dev.dynamicchunkie.visual.VisualRules.configure(config);
        culling = new dev.dynamicchunkie.culling.EntityCulling(config);
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player,world,hand,entity,hit) -> {
            if (player==net.minecraft.client.Minecraft.getInstance().player) manager.noteCombat();
            return net.minecraft.world.InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WorkCoordinator.beginTick();
            try {
                manager.tick(client);
                culling.tick(client);
                dev.dynamicchunkie.terrain.TerrainCulling.tick(client);
                dev.dynamicchunkie.visual.ParticlePolicy.tick(client);
                dev.dynamicchunkie.visual.EntityVisualPolicy.tick(client);
                manager.maintainCache();
            } finally {
                WorkCoordinator.endTick();
            }
            DebugOverlay.refresh(manager, client);
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            var pos = chunk.getPos();
            manager.unload(level, pos.x(), pos.z());
            dev.dynamicchunkie.sections.HotSoftSections.unload(level, pos.x(), pos.z());
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> culling.forget(entity));
        ClientLifecycleEvents.CLIENT_STOPPING.register(manager::restore);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> LOG.info("DynamicChunkie client initialized"));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { manager.restore(client); WorkCoordinator.reset(); DebugOverlay.clear(); culling.clear(); dev.dynamicchunkie.sections.HotSoftSections.clear();
            dev.dynamicchunkie.visual.EntityVisualPolicy.release();dev.dynamicchunkie.visual.ParticlePolicy.invalidate(); });
        DebugOverlay.register();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.register(literal("dynamicchunkie")
                .then(literal("diagnostics").then(literal("export").executes(ctx -> {
                    var snapshot = DebugOverlay.snapshot();
                    if (snapshot.debug().isEmpty() && snapshot.profiler().isEmpty()) {
                        ctx.getSource().sendFeedback(Component.literal("Enable diagnostics or an overlay and wait for a HUD sample."));
                        return 0;
                    }
                    var directory = ctx.getSource().getClient().gameDirectory.toPath().resolve("tmp/dynamicchunkie");
                    var output = directory.resolve("diagnostics-" + java.util.UUID.randomUUID() + ".json");
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            java.nio.file.Files.createDirectories(directory);
                            java.nio.file.Files.writeString(output, new com.google.gson.Gson().toJson(snapshot));
                            LOG.info("Saved diagnostics to {}", output);
                        } catch (java.io.IOException failure) {
                            LOG.error("Cannot export diagnostics", failure);
                        }
                    });
                    ctx.getSource().sendFeedback(Component.literal("Queued diagnostic snapshot: " + output));
                    return 1;
                })))
                .then(literal("toggle").executes(ctx -> {
                    manager.toggle(ctx.getSource().getClient());
                    ctx.getSource().sendFeedback(Component.literal("DynamicChunkie enabled: " + config.enabled));
                    return 1;
                }))
                .then(literal("debug").executes(ctx -> {
                    config.debugMode = !config.debugMode;
                    publishSettings();
                    config.save();
                    ctx.getSource().sendFeedback(Component.literal("DynamicChunkie debug: " + config.debugMode));
                    return 1;
                }))));
    }
}
