package dev.dynamicchunkie.adaptive;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceBudgetTest {
    private void fill(PerformanceBudget p,double frame,double cpu,double gpu) {
        for (int i=0;i<120;i++) p.record(frame,cpu,cpu,gpu);
    }
    @Test void profilerSummarizesWithoutChangingDistancePolicy() {
        var budget=new PerformanceBudget();
        for(int i=0;i<30;i++)budget.record(25,20,10,Double.NaN);
        budget.summarize(2_000_000_000L);
        assertEquals(25,budget.meanMs());assertEquals(25,budget.p95Ms());
        budget.reset();assertEquals(0,budget.meanMs());
    }
    @Test void sustainedGpuPressureLowersCeilingWithoutChangingEnvironmentFloor() {
        var p=new PerformanceBudget();fill(p,35,8,98);
        p.adjust(24,16,2,32,60,1_000_000_000L,true,true);
        p.adjust(24,16,2,32,60,7_000_000_000L,true,true);
        assertTrue(p.adjust(24,16,2,32,60,11_000_000_000L,true,true)<=14);
        assertEquals(2,p.adjust(2,2,2,32,60,12_000_000_000L,true,true));
    }
    @Test void cpuPressureCanThrottleWithoutGpuTelemetry() {
        var p=new PerformanceBudget();fill(p,35,30,0);
        p.adjust(16,16,2,32,60,1_000_000_000L,true,true);
        p.adjust(16,16,2,32,60,7_000_000_000L,true,true);
        assertTrue(p.cpuPressure());assertTrue(Double.isNaN(p.gpuPercent()));
    }
    @Test void stableVsyncCapIsNotMisreportedAsGpuOrCpuOverload() {
        var p=new PerformanceBudget();fill(p,33.333,2,15);
        p.adjust(16,16,2,32,60,1_000_000_000L,true,true);
        for (long second=7;second<30;second++)
            assertEquals(16,p.adjust(16,16,2,32,60,second*1_000_000_000L,true,true));
    }
    @Test void headroomRecoversTowardsButNeverBeyondEnvironmentalTarget() {
        var p=new PerformanceBudget();fill(p,35,30,98);
        p.adjust(16,16,2,32,60,1_000_000_000L,true,true);
        p.adjust(16,16,2,32,60,7_000_000_000L,true,true);
        int low=p.adjust(16,16,2,32,60,11_000_000_000L,true,true);
        fill(p,10,2,20);
        p.adjust(16,low,2,32,60,12_000_000_000L,true,true);
        int recovered=p.adjust(16,low,2,32,60,23_000_000_000L,true,true);
        assertTrue(recovered>low && recovered<=16);
    }
}
