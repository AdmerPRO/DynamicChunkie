package dev.dynamicchunkie.sections;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/** Index deferred coordinates in 8 x 4 x 8 section regions. No world references. */
final class ColdRegions {
    private final Long2ObjectOpenHashMap<LongOpenHashSet> regions = new Long2ObjectOpenHashMap<>();
    private static long region(long section) {
        return HotSoftGrid.key(HotSoftGrid.x(section)>>3, HotSoftGrid.y(section)>>2, HotSoftGrid.z(section)>>3);
    }
    void add(long section) { regions.computeIfAbsent(region(section), ignored -> new LongOpenHashSet()).add(section); }
    void remove(long section) {
        long key=region(section);
        var entries=regions.get(key);
        if (entries!=null && entries.remove(section) && entries.isEmpty()) regions.remove(key);
    }
    void near(int x,int y,int z,LongOpenHashSet output) {
        output.clear();
        for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
            var entries=regions.get(HotSoftGrid.key((x>>3)+dx,(y>>2)+dy,(z>>3)+dz));
            if (entries!=null) output.addAll(entries);
        }
    }
    void clear() {regions.clear();regions.trim();}
}
