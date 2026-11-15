package dev.dynamicchunkie;

import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native Minecraft widgets; Mod Menu is optional and no config-library dependency is needed. */
public final class ConfigScreen extends Screen {
    private final Screen parent;
    private DynamicChunkieConfig draft = DynamicChunkie.config().copy();
    private final Set<String> invalid = new HashSet<>();
    private final List<String> labels = new ArrayList<>();
    private int page, row, left, rowWidth;
    private Component error;

    public ConfigScreen(Screen parent) {
        super(Component.literal("DynamicChunkie"));
        this.parent = parent;
    }
    private Component text(String key) { return Component.translatable("dynamicchunkie.config." + key); }
    @Override protected void init() {
        labels.clear();
        row = 0;
        rowWidth = Math.min(340, width - 20);
        left = (width - rowWidth) / 2;
        addRenderableWidget(Button.builder(text("previous"), b -> changePage(-1))
                .bounds(left, 39, 55, 20).build());
        addRenderableWidget(Button.builder(text("next"), b -> changePage(1))
                .bounds(left + rowWidth - 55, 39, 55, 20).build());
        switch (page) {
            case 0 -> {
                toggle("enabled", () -> draft.enabled, v -> draft.enabled = v);
                toggle("preserveRenderCache", () -> draft.preserveRenderCache, v -> draft.preserveRenderCache = v);
                toggle("enableAltitudeFactor", () -> draft.enableAltitudeFactor, v -> draft.enableAltitudeFactor = v);
                toggle("enableSkyDetection", () -> draft.enableSkyDetection, v -> draft.enableSkyDetection = v);
                toggle("enableElytraOptimization", () -> draft.enableElytraOptimization, v -> draft.enableElytraOptimization = v);
                toggle("debugMode", () -> draft.debugMode, v -> draft.debugMode = v);
            }
            case 1 -> {
                integer("minRenderDistance", draft.minRenderDistance, v -> draft.minRenderDistance = v);
                integer("maxRenderDistance", draft.maxRenderDistance, v -> draft.maxRenderDistance = v);
                integer("tunnelDistance", draft.tunnelDistance, v -> draft.tunnelDistance = v);
                integer("smallCaveDistance", draft.smallCaveDistance, v -> draft.smallCaveDistance = v);
                integer("largeCaveDistance", draft.largeCaveDistance, v -> draft.largeCaveDistance = v);
                integer("undergroundOpenDistance", draft.undergroundOpenDistance, v -> draft.undergroundOpenDistance = v);
            }
            case 2 -> {
                integer("surfaceDistance", draft.surfaceDistance, v -> draft.surfaceDistance = v);
                integer("mountainDistance", draft.mountainDistance, v -> draft.mountainDistance = v);
                integer("highAltitudeDistance", draft.highAltitudeDistance, v -> draft.highAltitudeDistance = v);
            }
            case 3 -> {
                integer("analysisIntervalMs", draft.analysisIntervalMs, v -> draft.analysisIntervalMs = v);
                integer("transitionIntervalMs", draft.transitionIntervalMs, v -> draft.transitionIntervalMs = v);
                decimal("smoothingFactor", draft.smoothingFactor, v -> draft.smoothingFactor = v);
                integer("deadZoneChunks", draft.deadZoneChunks, v -> draft.deadZoneChunks = v);
                integer("targetStabilityMs", draft.targetStabilityMs, v -> draft.targetStabilityMs = v);
            }
        }
        int buttonWidth = (rowWidth - 8) / 3;
        addRenderableWidget(Button.builder(text("cancel"), b -> onClose()).bounds(left, height - 27, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(text("reset"), b -> {
            draft = new DynamicChunkieConfig();
            invalid.clear();
            error = null;
            rebuildWidgets();
        }).bounds(left + buttonWidth + 4, height - 27, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(text("save"), b -> save())
                .bounds(left + 2 * (buttonWidth + 4), height - 27, buttonWidth, 20).build());
    }
    private void changePage(int delta) {
        if (!invalid.isEmpty()) { error = text("invalid"); return; }
        page = Math.floorMod(page + delta, 4);
        error = null;
        rebuildWidgets();
    }
    private void toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
        int y = 66 + row++ * 22;
        addRenderableWidget(Button.builder(toggleText(key, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(toggleText(key, get.getAsBoolean()));
        }).bounds(left, y, rowWidth, 20).build());
        labels.add(null);
    }
    private Component toggleText(String key, boolean value) {
        return text(key).copy().append(": ").append(Component.translatable(value ? "options.on" : "options.off"));
    }
    private void integer(String key, int value, IntConsumer set) {
        number(key, Integer.toString(value), input -> set.accept(Integer.parseInt(input)));
    }
    private void decimal(String key, double value, DoubleConsumer set) {
        number(key, Double.toString(value), input -> {
            double parsed = Double.parseDouble(input.replace(',', '.'));
            if (!Double.isFinite(parsed)) throw new NumberFormatException();
            set.accept(parsed);
        });
    }
    private void number(String key, String value, Consumer<String> set) {
        int y = 66 + row++ * 22;
        var field = new EditBox(font, left + rowWidth - 90, y, 90, 20, text(key));
        field.setMaxLength(16);
        field.setValue(value);
        field.setResponder(input -> {
            try { set.accept(input); invalid.remove(key); field.setTextColor(0xFFFFFFFF); error = null; }
            catch (NumberFormatException e) { invalid.add(key); field.setTextColor(0xFFFF5555); error = text("invalid"); }
        });
        addRenderableWidget(field);
        labels.add(key);
    }
    private void save() {
        if (!invalid.isEmpty()) { error = text("invalid"); return; }
        if (DynamicChunkie.applyConfig(draft)) onClose();
        else error = text("saveFailed");
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(font, title, (width - font.width(title)) / 2, 10, 0xFFFFFFFF);
        Component pageTitle = text("page." + page);
        graphics.text(font, pageTitle, (width - font.width(pageTitle)) / 2, 45, 0xFFFFFFFF);
        for (int i = 0; i < labels.size(); i++)
            if (labels.get(i) != null) graphics.text(font, text(labels.get(i)), left, 72 + i * 22, 0xFFFFFFFF);
        if (error != null) graphics.text(font, error, left, height - 39, 0xFFFF5555);
    }
}
