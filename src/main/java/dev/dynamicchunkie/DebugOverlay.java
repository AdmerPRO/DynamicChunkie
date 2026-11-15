package dev.dynamicchunkie;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import java.util.Locale;

public final class DebugOverlay {
    public static void register(DynamicDistanceManager manager) {
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!manager.config.debugMode || client.options.hudHidden || client.player == null) return;
            EnvironmentScore s = manager.score();
            String[] lines = s == null ? new String[]{"DynamicChunkie: " + (manager.config.enabled ? "Waiting" : "Disabled")} :
                    new String[]{"DynamicChunkie",
                    "Current RD: " + client.options.getViewDistance().getValue(),
                    "Target RD: " + manager.target(),
                    "Environment: " + s.environment(),
                    String.format(Locale.ROOT, "Openness: %.2f  Horizontal: %.2f", s.openness(), s.horizontal()),
                    "Sky Visible: " + s.sky() + "  Altitude: " + (int)s.altitude(),
                    "Elytra: " + s.elytra(),
                    "Last Analysis: " + manager.analysisAgeMs() + "ms ago"};
            int y = 8;
            for (String line : lines) {
                context.fill(5, y - 1, 12 + client.textRenderer.getWidth(line), y + 10, 0xA0000000);
                context.drawTextWithShadow(client.textRenderer, line, 8, y, 0xFFFFFF);
                y += 12;
            }
        });
    }
}
