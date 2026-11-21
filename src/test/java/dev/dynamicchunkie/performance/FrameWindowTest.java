package dev.dynamicchunkie.performance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameWindowTest {
    @Test void wrapRetainsChronologicalTailAndCorrectStatistics() {
        var window = new FrameWindow(3);
        for (int i = 1; i <= 5; i++) window.add(i);
        assertEquals(3, window.size());
        assertEquals(3, window.oldest(0)); assertEquals(5, window.oldest(2));
        assertEquals(4, window.mean()); assertEquals(4, window.percentile(.5));
        assertEquals(5, window.percentile(.95));
        assertEquals(3, window.oldest(0)); // Sorting scratch must not alter history.
    }
    @Test void resetDropsOldValuesAndInvalidSamplesDoNotPolluteStatistics() {
        var window = new FrameWindow(4); window.add(30); window.clear();
        window.add(Double.NaN); window.add(Double.POSITIVE_INFINITY); window.add(-1); window.add(0);
        assertEquals(0, window.size()); assertEquals(0, window.mean()); assertEquals(0, window.percentile(.95));
        window.add(10); assertEquals(10, window.mean());
        assertThrows(IndexOutOfBoundsException.class, () -> window.oldest(1));
        assertThrows(IllegalArgumentException.class, () -> window.percentile(0));
        assertThrows(IllegalArgumentException.class, () -> new FrameWindow(0));
    }
}
