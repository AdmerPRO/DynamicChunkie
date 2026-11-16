package dev.dynamicchunkie.adaptive;

import dev.dynamicchunkie.EnvironmentScore;
import java.util.*;

/** Bounded, proximity-aware LRU of the mod's own analysis results, never stale world snapshots. */
public final class EnvironmentCache {
    public record Key(int x,int y,int z,int yaw,int pitch) {}
    private record Entry(EnvironmentScore result,long time,long revision) {}
    private final LinkedHashMap<Key,Entry> entries=new LinkedHashMap<>(16,.75f,true);
    private long hits;
    public EnvironmentScore get(Key key,long now,long revision,int ttlMs) {
        Entry value=entries.get(key);
        if (value==null) return null;
        if (value.revision!=revision || now-value.time>ttlMs*1_000_000L) {entries.remove(key);return null;}
        hits++;return value.result;
    }
    public void put(Key key,EnvironmentScore value,long now,long revision,int maxEntries,long budgetBytes) {
        // 1 KiB conservative charge covers key, result, map nodes and references.
        int limit=(int)Math.max(1,Math.min(maxEntries,budgetBytes/1024));
        entries.put(key,new Entry(value,now,revision));
        while (entries.size()>limit) {
            Key victim=null;double cost=-1;int age=entries.size();
            for (Key candidate:entries.keySet()) {
                double dx=(candidate.x-key.x)/4.0,dy=(candidate.y-key.y)/4.0,dz=(candidate.z-key.z)/4.0;
                double priority=age--+Math.min(1024,(dx*dx+dy*dy+dz*dz)/256);
                if (priority>cost) {cost=priority;victim=candidate;}
            }
            entries.remove(victim);
        }
    }
    public void clear() {entries.clear();}
    public int size() {return entries.size();}
    public long hits() {return hits;}
    public long chargedBytes() {return entries.size()*1024L;}
}
