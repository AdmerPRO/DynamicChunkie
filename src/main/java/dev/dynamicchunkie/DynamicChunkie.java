package dev.dynamicchunkie;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DynamicChunkie implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("DynamicChunkie");
    private static DynamicDistanceManager manager;
    private static dev.dynamicchunkie.culling.EntityCulling culling;
    public static dev.dynamicchunkie.culling.EntityCulling entityCulling() { return culling; }
    public static void onFrame() {
        if (manager!=null) manager.onFrame(net.minecraft.client.Minecraft.getInstance());
    }
    public static DynamicDistanceManager distanceManager() {return manager;}
    public static DynamicChunkieConfig configOrNull() {return manager==null?null:manager.config;}
    public static DynamicChunkieConfig config() { return manager.config; }
    public static boolean applyConfig(DynamicChunkieConfig value) {
        return manager.applyConfig(net.minecraft.client.Minecraft.getInstance(), value);
    }
    @Override public void onInitializeClient() {
        var config = DynamicChunkieConfig.load();
        RenderCache.configure(config);
        manager = new DynamicDistanceManager(config);
        culling = new dev.dynamicchunkie.culling.EntityCulling(config);
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player,world,hand,entity,hit) -> {
            if (player==net.minecraft.client.Minecraft.getInstance().player) manager.noteCombat();
            return net.minecraft.world.InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(manager::tick);
        ClientTickEvents.END_CLIENT_TICK.register(culling::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(manager::restore);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> LOG.info("DynamicChunkie client initialized"));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { manager.restore(client); culling.clear(); });
        DebugOverlay.register(manager);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.register(literal("dynamicchunkie")
                .then(literal("toggle").executes(ctx -> {
                    manager.toggle(ctx.getSource().getClient());
                    ctx.getSource().sendFeedback(Component.literal("DynamicChunkie enabled: " + config.enabled));
                    return 1;
                }))
                .then(literal("debug").executes(ctx -> {
                    config.debugMode = !config.debugMode;
                    config.save();
                    ctx.getSource().sendFeedback(Component.literal("DynamicChunkie debug: " + config.debugMode));
                    return 1;
                }))));
    }
}
