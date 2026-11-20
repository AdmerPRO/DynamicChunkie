package dev.dynamicchunkie.runtime;

import dev.dynamicchunkie.performance.FrameCpuSampler;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TelemetryTest {
    @Test void cpuWindowDividesByFramesAndRestartsAfterPause() {
        AtomicLong reads = new AtomicLong();
        var sampler = new FrameCpuSampler(() -> reads.incrementAndGet()*12_000_000);
        assertTrue(Double.isNaN(sampler.frame(0, 24)));
        assertTrue(Double.isNaN(sampler.frame(8, 24)));
        assertTrue(Double.isNaN(sampler.frame(16, 24)));
        assertEquals(4, sampler.frame(24, 24));
        assertEquals(2, reads.get());
        sampler.reset();
        assertTrue(Double.isNaN(sampler.frame(100, 24)));
    }

    @Test void modulesShareDeadlineAndBackOffAfterExpensiveEmptyPasses() {
        var clock = new AtomicLong();
        var scheduler = new AnalysisScheduler(clock::get);
        var module = AnalysisScheduler.Module.ENTITY_SELECTION;
        scheduler.beginTick(500_000, true);
        assertFalse(scheduler.admit(module, 50_000_000, false));
        for (int i=0; i<12; i++) {
            clock.addAndGet(250_000_000);
            scheduler.beginTick(500_000, true);
            assertTrue(scheduler.admit(module, 50_000_000, false));
            long start = clock.getAndAdd(600_000);
            scheduler.complete(module,start,0);
            assertFalse(scheduler.admit(AnalysisScheduler.Module.ENVIRONMENT, 0, true));
        }
        assertEquals(2, scheduler.snapshot().get(module.ordinal()).cooldownMultiplier());
        scheduler.complete(module,clock.get(),1);
        assertEquals(1, scheduler.snapshot().get(module.ordinal()).cooldownMultiplier());
    }
}
