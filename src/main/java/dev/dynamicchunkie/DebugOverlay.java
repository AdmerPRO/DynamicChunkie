package dev.dynamicchunkie;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import java.util.Locale;

public final class DebugOverlay {
    public static void register(DynamicDistanceManager manager) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                ResourceLocation.fromNamespaceAndPath("dynamicchunkie", "debug"), (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (!manager.config.debugMode || client.options.hideGui || client.player == null) return;
            EnvironmentScore s = manager.score();
            String[] lines = s == null ? new String[]{"DynamicChunkie: " + (manager.config.enabled ? "Waiting" : "Disabled")} :
                    new String[]{"DynamicChunkie",
                    "Current RD: " + client.options.renderDistance().get(),
                    "Target RD: " + manager.target(),
                    "Environment: " + s.environment(),
                    String.format(Locale.ROOT, "Openness: %.2f  Horizontal: %.2f", s.openness(), s.horizontal()),
                    "Sky Visible: " + s.sky() + "  Altitude: " + (int)s.altitude(),
                    "Elytra: " + s.elytra(),
                    "Last Analysis: " + manager.analysisAgeMs() + "ms ago",
                    "Render cache: " + RenderCache.status() +
                    "  Reused transitions: " + RenderCache.preservedChanges()};
            int y = 8;
            for (String line : lines) {
                context.fill(5, y - 1, 12 + client.font.width(line), y + 10, 0xA0000000);
                context.drawString(client.font, line, 8, y, 0xFFFFFFFF, true);
                y += 12;
            }
        });
    }
}
