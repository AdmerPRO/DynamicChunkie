package dev.dynamicchunkie.performance;

import java.util.Arrays;

/** Fixed-capacity frame history with allocation-free writes and reusable percentile scratch space. */
public final class FrameWindow {
    private final double[] values;
    private final double[] scratch;
    private int next;
    private int size;

    public FrameWindow(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Frame window capacity must be positive");
        values = new double[capacity];
        scratch = new double[capacity];
    }

    public void add(double milliseconds) {
        if (!Double.isFinite(milliseconds) || milliseconds <= 0) return;
        values[next] = milliseconds;
        next = (next + 1) % values.length;
        size = Math.min(size + 1, values.length);
    }

    public int size() { return size; }

    public double oldest(int offset) {
        if (offset < 0 || offset >= size) throw new IndexOutOfBoundsException(offset);
        return values[(next - size + offset + values.length) % values.length];
    }

    public double mean() {
        if (size == 0) return 0;
        double sum = 0;
        for (int i = 0; i < size; i++) sum += values[i];
        return sum / size;
    }

    public double percentile(double fraction) {
        if (!Double.isFinite(fraction) || fraction <= 0 || fraction > 1)
            throw new IllegalArgumentException("Percentile must be in (0, 1]");
        if (size == 0) return 0;
        System.arraycopy(values, 0, scratch, 0, size);
        Arrays.sort(scratch, 0, size);
        return scratch[(int) Math.ceil(fraction * size) - 1];
    }

    public void clear() { next = size = 0; }
}
