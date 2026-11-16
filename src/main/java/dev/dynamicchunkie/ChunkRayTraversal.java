package dev.dynamicchunkie;

/** Loaded-chunk guard for a bounded ray, using a 2D Amanatides-Woo traversal in X/Z. */
final class ChunkRayTraversal {
    @FunctionalInterface
    interface LoadedChunks {
        boolean contains(int chunkX, int chunkZ);
    }

    private ChunkRayTraversal() {}

    static boolean allLoaded(double startX, double startZ, double endX, double endZ, LoadedChunks loaded) {
        if (!valid(startX) || !valid(startZ) || !valid(endX) || !valid(endZ)) return false;

        int x = chunk(startX), z = chunk(startZ);
        int endChunkX = chunk(endX), endChunkZ = chunk(endZ);
        double dx = endX - startX, dz = endZ - startZ;
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dx);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dz);
        double nextX = x == endChunkX ? Double.POSITIVE_INFINITY : crossing(startX, dx, x, stepX);
        double nextZ = z == endChunkZ ? Double.POSITIVE_INFINITY : crossing(startZ, dz, z, stepZ);

        // Each iteration crosses at least one grid boundary. Current callers cast only 32 blocks.
        long remaining = Math.abs((long)endChunkX - x) + Math.abs((long)endChunkZ - z) + 1;
        while (remaining-- > 0) {
            if (!loaded.contains(x, z)) return false;
            if (x == endChunkX && z == endChunkZ) return true;

            double next = Math.min(nextX, nextZ);
            if (!Double.isFinite(next)) return false;
            // Exact corner crossing: enter the diagonal chunk, not either off-ray neighbour.
            // No epsilon: nearly equal crossings still traverse a real, thin intermediate chunk.
            if (nextX <= next) {
                x += stepX;
                nextX = x == endChunkX ? Double.POSITIVE_INFINITY : nextX + deltaX;
            }
            if (nextZ <= next) {
                z += stepZ;
                nextZ = z == endChunkZ ? Double.POSITIVE_INFINITY : nextZ + deltaZ;
            }
        }
        return false;
    }

    private static int chunk(double coordinate) { return (int)Math.floor(coordinate / 16); }

    private static boolean valid(double coordinate) {
        double chunk = Math.floor(coordinate / 16);
        return Double.isFinite(coordinate) && chunk >= Integer.MIN_VALUE && chunk <= Integer.MAX_VALUE;
    }

    private static double crossing(double start, double direction, int chunk, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = ((double)chunk + (step > 0 ? 1 : 0)) * 16;
        return (boundary - start) / direction;
    }
}
