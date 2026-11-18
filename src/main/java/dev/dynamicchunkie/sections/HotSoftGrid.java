package dev.dynamicchunkie.sections;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
/** Exact 3D halo; implicit neighbour queries avoid allocating 26 cells for every hot cell. */
public final class HotSoftGrid {
    public enum State {HOT,SOFT,COLD}
    private final LongOpenHashSet hot=new LongOpenHashSet();
    private int peakSize;
    public static long key(int x,int y,int z) {
        if(x< -2097152 || x>2097151 || z< -2097152 || z>2097151 || y< -524288 || y>524287)
            throw new IllegalArgumentException("Section coordinate outside packed range");
        return ((long)x&0x3fffffL)<<42 | ((long)z&0x3fffffL)<<20 | ((long)y&0xfffffL);
    }
    public static int x(long key){return (int)(key>>42);}
    public static int y(long key){return (int)(key<<44>>44);}
    public static int z(long key){return (int)(key<<22>>42);}
    public void clear(){hot.clear();}
    public void add(int x,int y,int z){hot.add(key(x,y,z));peakSize=Math.max(peakSize,hot.size());}
    public boolean isHot(long key){return hot.contains(key);}
    public State state(int x,int y,int z) {
        if(hot.contains(key(x,y,z)))return State.HOT;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
            int nx=x+dx,ny=y+dy,nz=z+dz;
            if(nx< -2097152 || nx>2097151 || nz< -2097152 || nz>2097151 || ny< -524288 || ny>524287)continue;
            if(hot.contains(key(nx,ny,nz)))return State.SOFT;
        }
        return State.COLD;
    }
    it.unimi.dsi.fastutil.longs.LongIterator hotIterator(){return hot.iterator();}
    public int hotCount(){return hot.size();}
    public boolean sameHot(HotSoftGrid other){return hot.equals(other.hot);}
    /** Report-time only: exact logical halo, including air and unloaded positions. */
    public int softCount() {
        var halo=new LongOpenHashSet();
        for(long cell:hot)for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
            int x=x(cell)+dx,y=y(cell)+dy,z=z(cell)+dz;
            if(x< -2097152 || x>2097151 || z< -2097152 || z>2097151 || y< -524288 || y>524287)continue;
            long k=key(x,y,z);if(!hot.contains(k))halo.add(k);
        }
        return halo.size();
    }
    public long chargedBytes(){return 64+peakSize*32L;}
}
