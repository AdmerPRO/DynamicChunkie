package dev.dynamicchunkie;
import dev.dynamicchunkie.culling.VoxelOcclusion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoxelOcclusionTest {
    @Test void opaqueWallBlocksButOpenAirDoesNot() {
        assertTrue(VoxelOcclusion.blocked(.5,.5,.5, 10.5,.5,.5, (x,y,z) -> x == 5 ? 1 : 0));
        assertFalse(VoxelOcclusion.blocked(.5,.5,.5, 10.5,.5,.5, (x,y,z) -> 0));
    }
    @Test void unknownSpaceDoesNotHideEntities() {
        assertFalse(VoxelOcclusion.blocked(.5,.5,.5, 10.5,.5,.5, (x,y,z) -> x == 3 ? -1 : x == 5 ? 1 : 0));
    }
    @Test void cornerOnlyContactDoesNotOcclude() {
        assertFalse(VoxelOcclusion.blocked(.5,.5,.5, 3.5,3.5,.5, (x,y,z) -> x == 1 && y == 0 ? 1 : 0));
    }
    @Test void raysWorkInNegativeCoordinates() {
        assertTrue(VoxelOcclusion.blocked(-.5,.5,.5, -10.5,.5,.5, (x,y,z) -> x == -5 ? 1 : 0));
    }
    @Test void cameraInsideBlockAndTargetCellStayVisible() {
        assertFalse(VoxelOcclusion.blocked(.5,.5,.5, 10.5,.5,.5, (x,y,z) -> x == 0 ? 1 : 0));
        assertFalse(VoxelOcclusion.blocked(.5,.5,.5, 10.5,.5,.5, (x,y,z) -> x == 10 ? 1 : 0));
    }
    @Test void traversalHasHardWorkLimit() {
        int[] calls = {0};
        assertFalse(VoxelOcclusion.blocked(.5,.5,.5, 10000,.5,.5, (x,y,z) -> {calls[0]++; return 0;}));
        assertTrue(calls[0] <= 257);
    }
}
