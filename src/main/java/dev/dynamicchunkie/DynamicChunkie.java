package dev.dynamicchunkie;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DynamicChunkie implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("DynamicChunkie");
    @Override public void onInitializeClient() {
        var config = DynamicChunkieConfig.load();
        var manager = new DynamicDistanceManager(config);
        ClientTickEvents.END_CLIENT_TICK.register(manager::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(manager::restore);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> manager.restore(client));
        DebugOverlay.register(manager);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.register(literal("dynamicchunkie")
                .then(literal("toggle").executes(ctx -> {
                    manager.toggle(ctx.getSource().getClient());
                    ctx.getSource().sendFeedback(Text.literal("DynamicChunkie enabled: " + config.enabled));
                    return 1;
                }))
                .then(literal("debug").executes(ctx -> {
                    config.debugMode = !config.debugMode;
                    config.save();
                    ctx.getSource().sendFeedback(Text.literal("DynamicChunkie debug: " + config.debugMode));
                    return 1;
                }))));
    }
}
