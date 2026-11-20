package dev.dynamicchunkie;

import dev.dynamicchunkie.visual.VisualProfiler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class DebugOverlay {
    public record Line(String text, int width) {}
    public record Snapshot(List<Line> debug, List<Line> profiler) {
        public Snapshot {
            debug = List.copyOf(debug);
            profiler = List.copyOf(profiler);
        }
    }
    private static final Snapshot EMPTY = new Snapshot(List.of(), List.of());
    private static Snapshot snapshot = EMPTY;
    private static long nextRefresh;
    private DebugOverlay() {}

    public static void clear() {
        snapshot = EMPTY;
        nextRefresh = 0;
    }

    public static Snapshot snapshot() { return snapshot; }

    public static void refresh(DynamicDistanceManager manager, Minecraft client) {
        var settings = DynamicChunkie.settings();
        if (client.player == null || (client.gui.hud.isHidden() && !settings.diagnosticsEnabled) || !(settings.debugMode || settings.profilerOverlay || settings.diagnosticsEnabled)) {
            clear();
            return;
        }
        long now = System.nanoTime();
        if (nextRefresh != 0 && now < nextRefresh) return;
        nextRefresh = now + settings.hudRefreshMs * 1_000_000L;
        var debug = settings.debugMode || settings.diagnosticsEnabled ? measure(client, debugLines(manager, client)) : List.<Line>of();
        var profiler = new ArrayList<Line>();
        if (settings.profilerOverlay || settings.diagnosticsEnabled) profiler.addAll(measure(client, VisualProfiler.lines(client)));
        if (settings.diagnosticsEnabled) {
            var changes = ((EnvironmentRevision) client.level).dynamicchunkie$changes();
            for (var kind : dev.dynamicchunkie.render.RenderChanges.Kind.values()) {
                String text = "World " + kind + ": " + changes.count(kind);
                profiler.add(new Line(text, client.font.width(text)));
            }
            for (var reason : dev.dynamicchunkie.diagnostics.EntityRejections.Reason.values()) {
                String text = "Entity rejects " + reason + ": " + dev.dynamicchunkie.diagnostics.EntityRejections.count(reason);
                profiler.add(new Line(text, client.font.width(text)));
            }
            for (var sample : dev.dynamicchunkie.runtime.WorkCoordinator.snapshot()) {
                String text = String.format(Locale.ROOT, "%s: %.0f us, %d calls, cooldown x%d",
                        sample.module(), sample.meanMicros(), sample.calls(), sample.cooldownMultiplier());
                profiler.add(new Line(text, client.font.width(text)));
            }
        }
        snapshot = new Snapshot(debug, profiler);
    }

    private static List<Line> measure(Minecraft client, String[] lines) {
        var result = new ArrayList<Line>(lines.length);
        for (String line : lines) result.add(new Line(line, client.font.width(line)));
        return result;
    }

    private static String[] debugLines(DynamicDistanceManager manager, Minecraft client) {
        EnvironmentScore s = manager.score();
            return s == null ? new String[]{"DynamicChunkie: " + (manager.config.enabled ? "Waiting" : "Disabled")} :
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
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("dynamicchunkie", "debug"), (graphics, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.gui.hud.isHidden()) return;
            var settings = DynamicChunkie.settings();
            if (settings.profilerOverlay) {
                int y = graphics.guiHeight() - 56 - snapshot.profiler().size() * 12;
                VisualProfiler.drawGraph(graphics, draw(graphics, client, snapshot.profiler(), y));
            }
            if (settings.debugMode) draw(graphics, client, snapshot.debug(), 8);
        });
    }

    private static int draw(GuiGraphicsExtractor graphics, Minecraft client, List<Line> lines, int y) {
        for (var line : lines) {
            graphics.fill(5, y - 1, 12 + line.width(), y + 10, 0xB0000000);
            graphics.text(client.font, line.text(), 8, y, 0xFFFFFFFF, true);
            y += 12;
        }
        return y;
    }
}
