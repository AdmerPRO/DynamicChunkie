package dev.dynamicchunkie.adaptive;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdaptivePoliciesTest {
    @Test void movementSelectsRequestedIntervalsAndCpuPressureSlowsAnalysis() {
        assertEquals(1500,AnalysisPolicy.interval(0,false,false,false,1500,750,250,true,900));
        assertEquals(750,AnalysisPolicy.interval(4,false,false,false,1500,750,250,true,900));
        assertEquals(250,AnalysisPolicy.interval(30,true,false,false,1500,750,250,true,900));
        assertEquals(500,AnalysisPolicy.interval(30,true,false,true,1500,750,250,true,900));
        assertEquals(900,AnalysisPolicy.interval(0,false,false,false,1500,750,250,false,900));
    }
    @Test void directionDistinguishesValleyAheadFromWallAhead() {
        assertTrue(AnalysisPolicy.directional(.5,1,1)>AnalysisPolicy.directional(.5,.1,.1));
    }
    @Test void predictionIsGatedByCurrentAndFutureClearance() {
        assertTrue(AnalysisPolicy.predictionBoost(30,1,1,1,1,1)>0);
        assertEquals(0,AnalysisPolicy.predictionBoost(30,.2,1,1,1,1));
        assertEquals(0,AnalysisPolicy.predictionBoost(30,1,.2,1,1,1));
        assertEquals(0,AnalysisPolicy.predictionBoost(30,1,1,1,.2,1));
        assertEquals(0,AnalysisPolicy.predictionBoost(3,1,1,1,1,1));
    }
    @Test void metadataReservationHasARealBoundButDoesNotEvictActiveRadius() {
        int r=MemoryBudget.reservationRadius(2,32,24,16);
        assertTrue(r<32);
        assertTrue((2L*r+1)*(2L*r+1)*24*1024<=16L*1024*1024);
        assertEquals(20,MemoryBudget.reservationRadius(20,32,24,16));
    }
    @Test void heapPressureConstrainsGrowthAndRespectsMinimum() {
        assertTrue(MemoryBudget.heapPressure(950_000_000,1_000_000_000));
        assertFalse(MemoryBudget.heapPressure(300_000_000,1_000_000_000));
        assertEquals(6,MemoryBudget.constrain(24,8,2,true));
        assertEquals(2,MemoryBudget.constrain(24,2,2,true));
    }
}
