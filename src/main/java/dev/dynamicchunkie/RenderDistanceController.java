package dev.dynamicchunkie;

import net.minecraft.client.Minecraft;

public final class RenderDistanceController {
    private final Minecraft client;
    public RenderDistanceController(Minecraft client) { this.client = client; }
    public int supportedMaximum() {
        Object callbacks = client.options.renderDistance().values();
        return callbacks instanceof net.minecraft.client.OptionInstance.IntRange range
                ? range.maxInclusive() : 32;
    }
    public int current() { return client.options.renderDistance().get(); }
    public void apply(int value) {
        // Use the option validator range, including the low-memory client limit.
        client.options.renderDistance().set(DynamicChunkieConfig.clamp(value, 2, supportedMaximum()));
        client.options.broadcastOptions();
    }
}
