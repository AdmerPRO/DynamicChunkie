package dev.dynamicchunkie.compat;
import dev.dynamicchunkie.terrain.TerrainCulling;
import it.unimi.dsi.fastutil.longs.*;
import java.util.*;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
/** Invalidate command batches only when our decision generation changes; never discard meshes. */
public final class SodiumTerrainCache {
    // Null value tracks a native region without allocating an exclusion set until its first skip.
    private static final Map<RenderRegion,LongOpenHashSet> regions=new WeakHashMap<>();
    private static long generation=-1,invalidations;
    public static long invalidations() {return invalidations;}
    private SodiumTerrainCache() {}
    public static void track(RenderRegion region) {if(!regions.containsKey(region))regions.put(region,null);}
    public static void count(RenderRegion region) {
        TerrainCulling.region(net.minecraft.core.SectionPos.asLong(region.getChunkX(),region.getChunkY(),region.getChunkZ()),
                region.getRenderList().getSectionsWithGeometryCount());
    }
    public static void hidden(RenderRegion region,int x,int y,int z) {
        regions.computeIfAbsent(region,r->new LongOpenHashSet()).add(net.minecraft.core.SectionPos.asLong(x>>4,y>>4,z>>4));
    }
    public static void cached(RenderRegion region) {
        if(!TerrainCulling.metricsEnabled())return;
        dev.dynamicchunkie.terrain.TerrainCulling.hook();count(region);
        var keys=regions.get(region);
        if(keys!=null)for(long key:keys)TerrainCulling.noteCachedSkip(key);
    }
    public static long chargedBytes() {
        long bytes=regions.size()*64L;
        for(var keys:regions.values())if(keys!=null)bytes+=keys.size()*32L;
        return bytes;
    }
    public static void clear(RenderRegion region) {
        var keys=regions.get(region);if(keys!=null)keys.clear();
    }
    public static void prepare(long version) {
        if(generation==version)return;
        generation=version;invalidations++;
        // Clearing a region's batch map also clears its recorded exclusions through the mixin.
        // clearAllCachedBatches only clears values through our hook, never this map's membership.
        for(RenderRegion region:regions.keySet())region.clearAllCachedBatches();
    }
}
