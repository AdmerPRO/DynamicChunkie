package dev.dynamicchunkie;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StableTargetTest {
    @Test void knownObstacleCanLowerButNeverRaiseAcceptedTargetImmediately() {
        var config=new DynamicChunkieConfig();config.targetStabilityMs=10000;
        var target=new StableTarget();assertEquals(24,target.update(24,1_000_000_000L,config));
        assertEquals(24,target.update(8,1_100_000_000L,config));
        assertEquals(8,target.restrict(8,config));assertEquals(8,target.restrict(16,config));
    }
    @Test void acceptsInitialTargetAndRequiresSustainedChange() {
        var policy = new StableTarget();
        var c = new DynamicChunkieConfig();
        assertEquals(8, policy.update(8, 0, c));
        assertEquals(8, policy.update(24, 1_000_000_000L, c));
        assertEquals(8, policy.update(24, 2_000_000_000L, c));
        assertEquals(24, policy.update(24, 2_500_000_000L, c));
    }
    @Test void doorwayOscillationDoesNotCommitDifferentDistances() {
        var policy = new StableTarget();
        var c = new DynamicChunkieConfig();
        policy.update(8, 0, c);
        for (int i = 1; i <= 20; i++)
            assertEquals(8, policy.update(i % 2 == 0 ? 4 : 16, i * 750_000_000L, c));
    }
    @Test void deadZoneCancelsPendingChanges() {
        var policy = new StableTarget();
        var c = new DynamicChunkieConfig();
        policy.update(8, 0, c);
        policy.update(24, 1_000_000_000L, c);
        assertEquals(8, policy.update(9, 2_000_000_000L, c));
        assertEquals(8, policy.update(24, 3_000_000_000L, c));
    }
    @Test void changedBoundsAndWorldResetDoNotKeepStaleTarget() {
        var policy = new StableTarget();
        var c = new DynamicChunkieConfig();
        policy.update(24, 0, c);
        c.maxRenderDistance = 12;
        assertEquals(12, policy.update(32, 10, c));
        policy.reset();
        assertEquals(2, policy.update(2, 20, c));
    }
}
