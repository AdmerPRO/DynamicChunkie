package dev.dynamicchunkie.sections;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
/** Reference counts cover the hot cell and its 26 neighbours. */
public final class HotSoftGrid {
    public enum State {HOT,SOFT,COLD}
    private final LongOpenHashSet hot=new LongOpenHashSet();
    private final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap coverage = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
    private int peakSize, peakCoverage;
    public static long key(int x,int y,int z) {
        if(x< -2097152 || x>2097151 || z< -2097152 || z>2097151 || y< -524288 || y>524287)
            throw new IllegalArgumentException("Section coordinate outside packed range");
        return ((long)x&0x3fffffL)<<42 | ((long)z&0x3fffffL)<<20 | ((long)y&0xfffffL);
    }
    public static int x(long key){return (int)(key>>42);}
    public static int y(long key){return (int)(key<<44>>44);}
    public static int z(long key){return (int)(key<<22>>42);}
    public void clear(){hot.clear();coverage.clear();}
    public void add(int x,int y,int z){
        long cell = key(x,y,z);
        if (hot.add(cell)) adjust(cell, 1);
    }
    private void adjust(long cell, int delta) {
        int x=x(cell), y=y(cell), z=z(cell);
        for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
            int nx=x+dx,ny=y+dy,nz=z+dz;
            if(nx< -2097152 || nx>2097151 || nz< -2097152 || nz>2097151 || ny< -524288 || ny>524287)continue;
            long neighbour=key(nx,ny,nz);
            if (coverage.addTo(neighbour,(byte)delta) + delta == 0) coverage.remove(neighbour);
        }
        peakCoverage=Math.max(peakCoverage,coverage.size());
        peakSize=Math.max(peakSize,hot.size());
    }
    boolean update(LongOpenHashSet next) {return update(next, null);}
    boolean update(LongOpenHashSet next, java.util.function.LongConsumer notify) {
        boolean changed = false;
        var iter = hot.iterator();
        while (iter.hasNext()) {
            long cell = iter.nextLong();
            if (!next.contains(cell)) {iter.remove();adjust(cell,-1);if(notify!=null)notify.accept(cell);changed=true;}
        }
        iter = next.iterator();
        while (iter.hasNext()) {
            long cell = iter.nextLong();
            if (hot.add(cell)) {adjust(cell,1);if(notify!=null)notify.accept(cell);changed=true;}
        }
        return changed;
    }
    public boolean isHot(long key){return hot.contains(key);}
    public State state(int x,int y,int z) {
        long cell=key(x,y,z);
        if(hot.contains(cell))return State.HOT;
        return coverage.containsKey(cell)?State.SOFT:State.COLD;
    }
    public int hotCount(){return hot.size();}
    public int softCount(){return coverage.size()-hot.size();}
    public long chargedBytes(){return 128+peakSize*32L+peakCoverage*32L;}
}
