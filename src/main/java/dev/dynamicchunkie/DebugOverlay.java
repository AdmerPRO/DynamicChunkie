package dev.dynamicchunkie;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import java.util.Locale;

public final class DebugOverlay {
    public static void register(DynamicDistanceManager manager) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("dynamicchunkie", "debug"), (context, tickCounter) -> {
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
                    String.format(Locale.ROOT,"Front/back/left/right: %.2f/%.2f/%.2f/%.2f",s.front(),s.back(),s.left(),s.right()),
                    String.format(Locale.ROOT,"Gaze: %.2f  Predictive boost: %.2f",s.gaze(),s.predicted()),
                    "Last Analysis: " + manager.analysisAgeMs() + "ms ago",
                    "Render cache: " + RenderCache.status() +
                    "  Reused transitions: " + RenderCache.preservedChanges(),
                    "Analysis: " + manager.analysisIntervalMs() + "ms  Cache hits: " + manager.cacheHits(),
                    String.format(Locale.ROOT,"Frame mean/p95: %.1f/%.1fms",manager.performance().meanMs(),manager.performance().p95Ms()),
                    "Budget: " + manager.performance().reason() + "  Teleport: " + manager.recovering(),
                    "Heap pressure: " + manager.memoryPressure()};
            int y = 8;
            for (String line : lines) {
                context.fill(5, y - 1, 12 + client.font.width(line), y + 10, 0xA0000000);
                context.text(client.font, line, 8, y, 0xFFFFFFFF, true);
                y += 12;
            }
        });
    }
}
