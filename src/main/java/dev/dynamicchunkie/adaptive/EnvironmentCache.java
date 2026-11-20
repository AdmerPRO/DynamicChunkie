package dev.dynamicchunkie.adaptive;

import java.util.*;

/** Bounded, proximity-aware LRU of the mod's own analysis results, never stale world snapshots. */
public final class EnvironmentCache<T> {
    public record Key(int x,int y,int z,int yaw,int pitch) {}
    private record Entry<T>(T result,long time,long revision) {}
    private LinkedHashMap<Key,Entry<T>> entries=new LinkedHashMap<>(16,.75f,true);
    private LinkedHashMap<Key,Long> ageOrder = new LinkedHashMap<>();
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Set<Key>> byChunk =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private long hits;
    private static long chunk(int x, int z) { return (long)x << 32 | Integer.toUnsignedLong(z); }
    private static long chunk(Key key) { return chunk(key.x >> 6,key.z >> 6); }

    private void remove(Key key) {
        entries.remove(key);
        ageOrder.remove(key);
        long chunk = chunk(key);
        var keys = byChunk.get(chunk);
        keys.remove(key);
        if (keys.isEmpty()) byChunk.remove(chunk);
    }
    public T get(Key key,long now,long revision,int ttlMs) {
        Entry<T> value=entries.get(key);
        if (value==null) return null;
        if (value.revision!=revision || now-value.time>ttlMs*1_000_000L) {remove(key);return null;}
        hits++;return value.result;
    }
    public void put(Key key,T value,long now,long revision,int maxEntries,long budgetBytes) {
        // 1 KiB conservative charge covers key, result, map nodes and references.
        int limit=(int)Math.max(1,Math.min(maxEntries,budgetBytes/1024));
        ageOrder.putLast(key, now);
        if (entries.put(key,new Entry<>(value,now,revision)) == null)
            byChunk.computeIfAbsent(chunk(key), ignored -> new HashSet<>()).add(key);
        while (entries.size()>limit) {
            Key victim=null;double cost=-1;int age=entries.size();
            for (Key candidate:entries.keySet()) {
                double dx=(candidate.x-key.x)/4.0,dy=(candidate.y-key.y)/4.0,dz=(candidate.z-key.z)/4.0;
                double priority=age--+Math.min(1024,(dx*dx+dy*dy+dz*dz)/256);
                if (priority>cost) {cost=priority;victim=candidate;}
            }
            remove(victim);
        }
    }
    public int expire(long now, int ttlMs, int limit) {
        int removed = 0;
        while (removed < limit && !ageOrder.isEmpty()) {
            var oldest = ageOrder.firstEntry();
            // Access-order reads would change the LRU ranking. The timestamp index is independent.
            if (now-oldest.getValue() <= ttlMs*1_000_000L) break;
            remove(oldest.getKey());
            removed++;
        }
        return removed;
    }
    public void removeChunk(int x, int z) {
        var keys = byChunk.remove(chunk(x,z));
        if (keys != null) for (var key : keys) {
            entries.remove(key);
            ageOrder.remove(key);
        }
    }
    public void clear() { entries.clear(); ageOrder.clear(); byChunk.clear(); }
    public void release() {
        entries = new LinkedHashMap<>(16,.75f,true);
        ageOrder = new LinkedHashMap<>();
        byChunk.clear();
        byChunk.trim();
    }
    public int size() {return entries.size();}
    public long hits() {return hits;}
    public long chargedBytes() {return entries.size()*1024L;}
}
