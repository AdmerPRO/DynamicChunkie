package dev.dynamicchunkie.adaptive;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistanceTransitionsTest {
    private final Object world=new Object();
    private void observe(DistanceTransitions t,double x,long now,boolean hurt) {
        t.observe(world,x,64,0,0,hurt,now,true,48,5000);
    }
    @Test void normalGrowthIsTwoPerSecondAndDecreaseIsImmediate() {
        var t=new DistanceTransitions();t.pause(1_000_000_000L);
        assertEquals(2,t.next(2,16,1_999_000_000L,1000));
        assertEquals(4,t.next(2,16,2_000_000_000L,1000));
        assertEquals(6,t.next(4,16,3_000_000_000L,1000));
        assertEquals(2,t.next(16,2,3_001_000_000L,1000));
    }
    @Test void teleportStartsAtMinimumAndRecoversOnePerSecond() {
        var t=new DistanceTransitions();
        assertTrue(t.observe(world,0,64,0,0,false,1_000_000_000L,true,48,5000));
        assertEquals(2,t.next(2,16,1_999_000_000L,1000));
        assertEquals(3,t.next(2,16,2_000_000_000L,1000));
        assertEquals(4,t.next(3,16,3_000_000_000L,1000));
    }
    @Test void combatSkipsTeleportResetAndCancelsRecovery() {
        var t=new DistanceTransitions();t.combat(1_000_000_000L);
        assertFalse(t.observe(world,128,64,0,0,false,2_000_000_000L,true,48,5000));
        var recovery=new DistanceTransitions();observe(recovery,0,1_000_000_000L,false);
        recovery.combat(1_100_000_000L);
        assertEquals(16,recovery.next(2,16,1_101_000_000L,1000));
    }
    @Test void displacementAndDimensionsDetectTeleportButFastMovementDoesNot() {
        var t=new DistanceTransitions();
        t.observe(world,0,64,0,0,false,1_000_000_000L,false,48,5000);
        assertFalse(t.observe(world,60,64,0,20,false,2_000_000_000L,true,48,5000));
        assertTrue(t.observe(world,200,64,0,0,false,3_000_000_000L,true,48,5000));
        assertTrue(t.observe(new Object(),200,64,0,0,false,4_000_000_000L,true,48,5000));
    }
    @Test void pausesDoNotAccumulateAnIncreaseBurst() {
        var t=new DistanceTransitions();t.pause(1_000_000_000L);
        assertEquals(4,t.next(2,32,100_000_000_000L,1000));
    }
}
