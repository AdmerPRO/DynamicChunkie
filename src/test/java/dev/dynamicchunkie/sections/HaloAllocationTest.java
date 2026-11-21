package dev.dynamicchunkie.sections;

import com.sun.management.ThreadMXBean;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HaloAllocationTest {
    @Test void compareIndexBuildAllocationsForEqualHotMembership() {
        var bean=ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        assumeTrue(bean!=null && bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled());
        measure(bean,1,false);
        measure(bean,1,true);
        measure(bean,4,false);
        measure(bean,4,true);
    }

    private void measure(ThreadMXBean bean,int stride,boolean report) {
        long before=bean.getCurrentThreadAllocatedBytes();
        var old=new LongOpenHashSet();
        for (int x=0;x<32;x++) for (int z=0;z<32;z++) old.add(HotSoftGrid.key(x*stride,0,z*stride));
        long implicitBytes=bean.getCurrentThreadAllocatedBytes()-before;
        before=bean.getCurrentThreadAllocatedBytes();
        var indexed=new HotSoftGrid();
        for (int x=0;x<32;x++) for (int z=0;z<32;z++) indexed.add(x*stride,0,z*stride);
        long indexedBytes=bean.getCurrentThreadAllocatedBytes()-before;
        assertEquals(old.size(),indexed.hotCount());
        assertEquals(stride==1?34*34*3-1024:26*1024,indexed.softCount());
        if (report) System.out.printf("Halo allocation: stride=%d hot=%d soft=%d implicitBytes=%d indexedBytes=%d%n",
                stride,old.size(),indexed.softCount(),implicitBytes,indexedBytes);
    }
}
