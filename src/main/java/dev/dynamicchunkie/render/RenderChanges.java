package dev.dynamicchunkie.render;

/** Per-client-world revisions. Only geometry inputs invalidate an environmental ray sample. */
public final class RenderChanges {
    public enum Kind { BLOCK, CHUNK_LOAD, CHUNK_UNLOAD, CAMERA, MESH_READINESS, RENDERER_RELOAD }
    private final long[] counts = new long[Kind.values().length];
    private long worldGeometry;

    public void record(Kind kind) {
        counts[kind.ordinal()]++;
        if (kind == Kind.BLOCK || kind == Kind.CHUNK_LOAD || kind == Kind.CHUNK_UNLOAD) worldGeometry++;
    }

    public long count(Kind kind) { return counts[kind.ordinal()]; }
    public long geometry() { return worldGeometry; }
}
