package dev.dynamicchunkie;

import net.minecraft.client.MinecraftClient;

public final class RenderDistanceController {
    private final MinecraftClient client;
    public RenderDistanceController(MinecraftClient client) { this.client = client; }
    public int supportedMaximum() {
        Object callbacks = client.options.getViewDistance().getCallbacks();
        return callbacks instanceof net.minecraft.client.option.SimpleOption.ValidatingIntSliderCallbacks range
                ? range.maxInclusive() : 32;
    }
    public int current() { return client.options.getViewDistance().getValue(); }
    public void apply(int value) {
        // Use the option validator range, including the low-memory client limit.
        client.options.getViewDistance().setValue(DynamicChunkieConfig.clamp(value, 2, supportedMaximum()));
        client.options.sendClientSettings();
    }
}
