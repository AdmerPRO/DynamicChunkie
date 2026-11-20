package dev.dynamicchunkie.benchmark;

import com.sun.management.ThreadMXBean;
import dev.dynamicchunkie.visual.NearestSelection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;

/** Measures sorting and heap selection on the same frozen farm positions. No render or world reads inside timing. */
final class SelectionComparison {
    private record Entry(int id,int type,double distance) {}
    record Sample(int entities,int limit,int pair,boolean heap,long cpuNs,long wallNs,long allocatedBytes,long collections) {}
    private final ArrayList<Entry> sorted = new ArrayList<>();
    private final IntOpenHashSet kept = new IntOpenHashSet();
    private final List<NearestSelection<Entry>> heaps;
    private static final Comparator<Entry> DISTANCE = Comparator.comparingDouble(Entry::distance);
    private static final java.lang.management.ThreadMXBean CPU = ManagementFactory.getThreadMXBean();
    private static final List<java.lang.management.GarbageCollectorMXBean> GC = ManagementFactory.getGarbageCollectorMXBeans();

    private SelectionComparison(int limit) { heaps = List.of(new NearestSelection<>(limit),new NearestSelection<>(limit)); }

    static List<Sample> measure(Minecraft client,int limit,long seed) {
        var entries = new ArrayList<Entry>();
        var camera = client.gameRenderer.mainCamera().position();
        for (var entity : client.level.entitiesForRendering()) {
            int type;
            if (entity.getType() == net.minecraft.world.entity.EntityTypes.COW) type = 0;
            else if (entity.getType() == net.minecraft.world.entity.EntityTypes.PIG) type = 1;
            else continue;
            entries.add(new Entry(entity.getId(),type,entity.distanceToSqr(camera)));
        }
        var worker = new SelectionComparison(limit);
        var order = new Random(seed);
        var samples = new ArrayList<Sample>();
        for (int pair = -8; pair < 16; pair++) {
            boolean first = order.nextBoolean();
            var expected = new IntOpenHashSet();
            for (int phase = 0; phase < 2; phase++) {
                boolean heap = phase == 0 ? first : !first;
                long gc = collections(), bytes = allocated(), cpu = cpu(), start = System.nanoTime();
                if (heap) worker.heap(entries); else worker.sort(entries,limit);
                long elapsed = System.nanoTime()-start, cpuDelta = delta(cpu,cpu()), bytesDelta = delta(bytes,allocated());
                if (pair >= 0) samples.add(new Sample(entries.size(),limit,pair,heap,cpuDelta,elapsed,bytesDelta,collections()-gc));
                if (phase == 0) expected.addAll(worker.kept);
                else if (!expected.equals(worker.kept)) throw new AssertionError("Selectors disagree on selected IDs");
            }
        }
        return samples;
    }

    private void sort(List<Entry> entries,int limit) {
        kept.clear(); sorted.clear(); sorted.addAll(entries); sorted.sort(DISTANCE);
        int[] counts = new int[2];
        for (var entry : sorted) if (counts[entry.type()]++ < limit) kept.add(entry.id());
    }
    private void heap(List<Entry> entries) {
        kept.clear();
        for (var group : heaps) group.clear();
        for (var entry : entries) heaps.get(entry.type()).offer(entry,entry.distance());
        for (var group : heaps)
            for (int index = 0; index < group.size(); index++) kept.add(group.valueAt(index).id());
    }
    private static long collections() {
        long total = 0;
        for (var gc : GC) total += Math.max(0,gc.getCollectionCount());
        return total;
    }
    private static long allocated() {
        return CPU instanceof ThreadMXBean bean && bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled()
                ? bean.getCurrentThreadAllocatedBytes() : -1;
    }
    private static long cpu() { return CPU.isCurrentThreadCpuTimeSupported() ? CPU.getCurrentThreadCpuTime() : -1; }
    private static long delta(long start,long end) { return start >= 0 && end >= start ? end-start : -1; }
}
