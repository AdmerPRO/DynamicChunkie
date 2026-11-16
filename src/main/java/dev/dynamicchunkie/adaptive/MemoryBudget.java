package dev.dynamicchunkie.adaptive;

/** Soft native-cache reservation budget plus an actual JVM headroom guard. Not a VRAM meter. */
public final class MemoryBudget {
    private MemoryBudget() {}
    public static int reservationRadius(int actual,int maximum,int sectionHeight,int budgetMb) {
        long bytes=budgetMb*1024L*1024;
        int radius=Math.max(actual,maximum);
        // Reservation accounting: 1 KiB per vanilla section slot; compiled mesh sizes are engine-owned.
        while (radius>actual && (2L*radius+1)*(2L*radius+1)*Math.max(1,sectionHeight)*1024>bytes) radius--;
        return radius;
    }
    public static boolean heapPressure(long used,long max) {
        return max>0 && max-used<Math.max(64L*1024*1024,max/10);
    }
    public static int constrain(int desired,int current,int minimum,boolean pressure) {
        return pressure?Math.max(minimum,Math.min(desired,current-2)):desired;
    }
}
