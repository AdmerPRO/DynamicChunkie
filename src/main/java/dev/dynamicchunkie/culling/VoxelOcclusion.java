package dev.dynamicchunkie.culling;

/** Bounded voxel traversal. Unknown space and boundary-only contacts never occlude. */
public final class VoxelOcclusion {
    @FunctionalInterface public interface Blocks {
        /** 1: opaque full cube; 0: clear; -1: unknown/unloaded. */
        int at(int x, int y, int z);
    }
    private VoxelOcclusion() {}
    public static boolean blocked(double ax, double ay, double az, double bx, double by, double bz, Blocks blocks) {
        int x = floor(ax), y = floor(ay), z = floor(az);
        int endX = floor(bx), endY = floor(by), endZ = floor(bz);
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        int sx = Double.compare(dx, 0), sy = Double.compare(dy, 0), sz = Double.compare(dz, 0);
        double tx = crossing(ax, x, dx, sx), ty = crossing(ay, y, dy, sy), tz = crossing(az, z, dz, sz);
        double stepX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double stepY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double stepZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        // Camera inside a solid block, unavailable origin or zero ray: stay visible.
        if (blocks.at(x, y, z) != 0) return false;
        for (int visited = 0; visited < 256; visited++) {
            if (x == endX && y == endY && z == endZ) return false;
            double next = Math.min(tx, Math.min(ty, tz));
            if (next > 1 || !Double.isFinite(next)) return false;
            // Skip cells merely touched at an edge/corner (conservative).
            if (tx <= next) { x += sx; tx += stepX; }
            if (ty <= next) { y += sy; ty += stepY; }
            if (tz <= next) { z += sz; tz += stepZ; }
            if (x == endX && y == endY && z == endZ) return false;
            int state = blocks.at(x, y, z);
            if (state < 0) return false;
            if (state > 0) return true;
        }
        return false;
    }
    private static int floor(double value) { return (int)Math.floor(value); }
    private static double crossing(double value, int cell, double delta, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        return ((step > 0 ? cell + 1 : cell) - value) / delta;
    }
}
