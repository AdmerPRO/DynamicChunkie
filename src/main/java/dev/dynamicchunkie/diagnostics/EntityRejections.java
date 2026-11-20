package dev.dynamicchunkie.diagnostics;

import dev.dynamicchunkie.DynamicChunkie;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/** One reason per entity per extracted frame. Native rejections may also include distance or renderer policy. */
public final class EntityRejections {
    public enum Reason { NATIVE, TYPE_LIMIT, DISTANCE, OCCLUSION }
    private static final IntOpenHashSet counted = new IntOpenHashSet();
    private static final int[] counts = new int[Reason.values().length];
    private static boolean enabled;
    private EntityRejections() {}
    public static void begin() {
        var settings = DynamicChunkie.settings();
        enabled = settings != null && settings.diagnosticsEnabled;
        if (!counted.isEmpty()) counted.clear();
        if (enabled) java.util.Arrays.fill(counts,0);
    }
    public static void record(Reason reason,int id) {
        if (enabled && counted.size() < DynamicChunkie.settings().entityMetadataLimitKiB * 8 && counted.add(id))
            counts[reason.ordinal()]++;
    }
    public static int count(Reason reason) { return counts[reason.ordinal()]; }
    public static void release() { counted.clear(); counted.trim(); }
}
