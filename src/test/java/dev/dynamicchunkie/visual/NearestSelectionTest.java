package dev.dynamicchunkie.visual;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NearestSelectionTest {
    @Test void matchesStableFullSortAcrossLimitsTiesAndHeapGrowth() {
        var random = new Random(12345);
        var distances = new ArrayList<Integer>();
        for (int i = 0; i < 3000; i++) distances.add(random.nextInt(100));
        for (int limit : new int[]{1,2,17,100,5000}) {
            var heap = new NearestSelection<Integer>(limit);
            var expected = new ArrayList<Integer>();
            for (int i = 0; i < distances.size(); i++) { expected.add(i); heap.offer(i,distances.get(i)); }
            expected.sort(Comparator.comparingInt(distances::get));
            var actual = new HashSet<Integer>();
            for (int i = 0; i < heap.size(); i++) actual.add(heap.valueAt(i));
            assertEquals(new HashSet<>(expected.subList(0,Math.min(limit,expected.size()))),actual);
        }
    }
    @Test void clearReusesCapacityAndRejectsNonfiniteDistances() {
        var heap = new NearestSelection<String>(2);
        heap.offer("old",5); heap.clear();
        heap.offer("nan",Double.NaN); heap.offer("infinity",Double.POSITIVE_INFINITY);
        heap.offer("near",1); heap.offer("same",1); heap.offer("later",1);
        assertEquals(2,heap.size());
        assertEquals(Set.of("near","same"),Set.of(heap.valueAt(0),heap.valueAt(1)));
        assertThrows(IndexOutOfBoundsException.class, () -> heap.valueAt(2));
    }
}
