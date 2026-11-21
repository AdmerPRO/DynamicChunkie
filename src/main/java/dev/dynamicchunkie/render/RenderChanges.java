package dev.dynamicchunkie.render;

/** Per-client-world revisions. Only geometry inputs invalidate an environmental ray sample. */
public final class RenderChanges {
    public enum Kind { BLOCK, CHUNK_LOAD, CHUNK_UNLOAD, CAMERA, MESH_READINESS, RENDERER_RELOAD }
    private final long[] counts = new long[Kind.values().length];
    private long worldGeometry;

    // Fixed-size stamps: hash collisions only cause extra invalidation. Never retain unloaded chunks.
    private final long[] sections = new long[8192], columns = new long[2048], skyColumns = new long[2048];
    private static int bucket(int x,int y,int z,int mask) {
        return (int)it.unimi.dsi.fastutil.HashCommon.mix(net.minecraft.core.SectionPos.asLong(x,y,z)) & mask;
    }
    public void block(int x,int y,int z) {
        record(Kind.BLOCK);
        sections[bucket(x,y,z,8191)] = worldGeometry;
        skyColumns[bucket(x,0,z,2047)] = worldGeometry;
    }
    public void chunk(Kind kind,int x,int z) {
        record(kind);
        columns[bucket(x,0,z,2047)] = worldGeometry;
    }
    public long columnVersion(int x,int z) {
        return columns[bucket(x,0,z,columns.length-1)];
    }
    public long region(int minX,int minY,int minZ,int maxX,int maxY,int maxZ,boolean sky) {
        long version = 0;
        for (int x=minX;x<=maxX;x++) for (int z=minZ;z<=maxZ;z++) {
            int column = bucket(x,0,z,2047);
            version = Math.max(version,columns[column]);
            if (sky) version = Math.max(version,skyColumns[column]);
            else for (int y=minY;y<=maxY;y++) version=Math.max(version,sections[bucket(x,y,z,8191)]);
        }
        return version;
    }

    public void record(Kind kind) {
        counts[kind.ordinal()]++;
        if (kind == Kind.BLOCK || kind == Kind.CHUNK_LOAD || kind == Kind.CHUNK_UNLOAD) worldGeometry++;
    }

    public long count(Kind kind) { return counts[kind.ordinal()]; }
    public long geometry() { return worldGeometry; }
}
