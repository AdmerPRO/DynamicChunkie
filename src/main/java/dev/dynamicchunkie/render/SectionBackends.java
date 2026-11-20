package dev.dynamicchunkie.render;

import dev.dynamicchunkie.RenderCache;
import org.jetbrains.annotations.Nullable;

/** Lazy backend boundary: the optional Sodium implementation is never linked on vanilla. */
public final class SectionBackends {
    private static @Nullable SectionBackend backend;
    private SectionBackends() {}

    public static SectionBackend current() {
        if (backend == null) {
            if (RenderCache.sodiumPresent() && !RenderCache.sodiumSupported())
                throw new IllegalStateException("No section adapter for this Sodium version");
            backend = RenderCache.sodiumSupported()
                    ? new dev.dynamicchunkie.compat.SodiumHotSoft() : new VanillaSectionBackend();
        }
        return backend;
    }
}
