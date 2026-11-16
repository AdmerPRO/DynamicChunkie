package dev.dynamicchunkie;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkRayTraversalTest {
    private record Chunk(int x, int z) {}

    private List<Chunk> visits(double ax, double az, double bx, double bz) {
        var visited = new ArrayList<Chunk>();
        assertTrue(ChunkRayTraversal.allLoaded(ax, az, bx, bz, (x,z) -> {
            visited.add(new Chunk(x,z)); return true;
        }));
        assertEquals(new HashSet<>(visited).size(), visited.size(), "duplicate lookup");
        return visited;
    }

    @Test void diagonalIgnoresUnloadedOffRayNeighbours() {
        var loaded = Set.of(new Chunk(0,0), new Chunk(1,1));
        assertTrue(ChunkRayTraversal.allLoaded(8,8,24,24, (x,z) -> loaded.contains(new Chunk(x,z))));
        assertEquals(List.of(new Chunk(0,0), new Chunk(1,1)), visits(8,8,24,24));
    }
    @Test void nonCornerDiagonalChecksTheActualIntermediateChunk() {
        assertEquals(List.of(new Chunk(0,0), new Chunk(1,0), new Chunk(1,1)), visits(15,1,17,17));
        assertFalse(ChunkRayTraversal.allLoaded(15,1,17,17, (x,z) -> !(x==1 && z==0)));
    }
    @Test void negativeDiagonalUsesFloorAndSkipsCornerNeighbours() {
        assertEquals(List.of(new Chunk(-1,-1), new Chunk(-2,-2)), visits(-1,-1,-17,-17));
    }
    @Test void horizontalRaysTraverseInEitherDirection() {
        assertEquals(List.of(new Chunk(0,0), new Chunk(1,0), new Chunk(2,0)), visits(.5,.5,32.5,.5));
        assertEquals(List.of(new Chunk(2,0), new Chunk(1,0), new Chunk(0,0)), visits(32.5,.5,.5,.5));
    }
    @Test void fixedHorizontalPositionNeedsOnlyOneChunk() {
        assertEquals(List.of(new Chunk(1,-1)), visits(16,-.1,16,-.1));
    }
    @Test void exactStartAndEndBoundariesUseFloorOwnership() {
        assertEquals(List.of(new Chunk(1,0), new Chunk(0,0)), visits(16,8,15,8));
        assertEquals(List.of(new Chunk(0,0), new Chunk(1,0)), visits(8,8,16,8));
        assertEquals(List.of(new Chunk(2,0), new Chunk(1,0)), visits(32,8,16,8));
    }
    @Test void crossingZeroIncludesBothSides() {
        assertEquals(List.of(new Chunk(-1,-1), new Chunk(0,-1)), visits(-.1,-.1,.1,-.1));
    }
    @Test void missingOriginOrDestinationRejectsTheRay() {
        assertFalse(ChunkRayTraversal.allLoaded(8,8,24,24, (x,z) -> x != 0));
        assertFalse(ChunkRayTraversal.allLoaded(8,8,24,24, (x,z) -> x != 1));
    }
    @Test void nearlyCoincidentCrossingsMustNotBeMerged() {
        assertEquals(List.of(new Chunk(0,0), new Chunk(0,1), new Chunk(1,1)),
                visits(.5,.5,31.5,31.5+1e-8));
        assertFalse(ChunkRayTraversal.allLoaded(.5,.5,31.5,31.5+1e-8, (x,z) -> !(x==0 && z==1)));
    }
    @Test void invalidCoordinatesDoNotQueryChunks() {
        ChunkRayTraversal.LoadedChunks unexpected = (x,z) -> { fail("invalid ray queried chunks"); return false; };
        assertFalse(ChunkRayTraversal.allLoaded(Double.NaN,0,0,0,unexpected));
        assertFalse(ChunkRayTraversal.allLoaded(0,0,Double.POSITIVE_INFINITY,0,unexpected));
        assertFalse(ChunkRayTraversal.allLoaded(0,0,Double.MAX_VALUE,0,unexpected));
    }
    @Test void traversalMatchesIndependentSegmentRectangleIntersections() {
        var random = new Random(204367);
        for (int i=0; i<5000; i++) {
            double ax=random.nextDouble()*160-80, az=random.nextDouble()*160-80;
            double bx=ax+random.nextDouble()*64-32, bz=az+random.nextDouble()*64-32;
            if (i%10==0) bx=Math.floor(bx/16)*16;
            if (i%13==0) bz=Math.floor(bz/16)*16;
            var expected = new HashSet<Chunk>();
            expected.add(new Chunk(chunk(ax),chunk(az)));
            expected.add(new Chunk(chunk(bx),chunk(bz)));
            for (int x=Math.min(chunk(ax),chunk(bx)); x<=Math.max(chunk(ax),chunk(bx)); x++)
                for (int z=Math.min(chunk(az),chunk(bz)); z<=Math.max(chunk(az),chunk(bz)); z++)
                    if (intersects(ax,az,bx,bz,x,z)) expected.add(new Chunk(x,z));
            assertEquals(expected,new HashSet<>(visits(ax,az,bx,bz)), "segment " + i);
        }
    }
    private int chunk(double value) { return (int)Math.floor(value/16); }
    private boolean intersects(double ax,double az,double bx,double bz,int x,int z) {
        double enter=0, exit=1;
        double[] starts={ax,az}, deltas={bx-ax,bz-az}, mins={x*16.0,z*16.0};
        for (int axis=0; axis<2; axis++) {
            if (deltas[axis]==0) {
                if (starts[axis]<mins[axis] || starts[axis]>=mins[axis]+16) return false;
            } else {
                double a=(mins[axis]-starts[axis])/deltas[axis];
                double b=(mins[axis]+16-starts[axis])/deltas[axis];
                enter=Math.max(enter,Math.min(a,b));
                exit=Math.min(exit,Math.max(a,b));
            }
        }
        return enter<exit;
    }
}
