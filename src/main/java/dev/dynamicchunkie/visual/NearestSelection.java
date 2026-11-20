package dev.dynamicchunkie.visual;

import java.util.Arrays;

/** Reusable bounded max heap. Keeps the nearest K values in O(N log K), with stable ties. */
public final class NearestSelection<T> {
    private final int limit;
    private Object[] values;
    private double[] distances;
    private long[] order;
    private int size;
    private long sequence;

    public NearestSelection(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Selection limit must be positive");
        this.limit = limit;
        int capacity = Math.min(16, limit);
        values = new Object[capacity]; distances = new double[capacity]; order = new long[capacity];
    }

    public void offer(T value, double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0) return;
        long arrival = sequence++;
        if (size == limit) {
            if (distanceSquared >= distances[0]) return;
            values[0] = value; distances[0] = distanceSquared; order[0] = arrival;
            sink();
            return;
        }
        if (size == values.length) {
            int capacity = Math.min(limit, values.length * 2);
            values = Arrays.copyOf(values, capacity);
            distances = Arrays.copyOf(distances, capacity);
            order = Arrays.copyOf(order, capacity);
        }
        int child = size++;
        values[child] = value; distances[child] = distanceSquared; order[child] = arrival;
        while (child > 0) {
            int parent = (child - 1) / 2;
            if (!worse(child, parent)) break;
            swap(child, parent); child = parent;
        }
    }

    private void sink() {
        int parent = 0;
        while (parent * 2 + 1 < size) {
            int child = parent * 2 + 1;
            if (child + 1 < size && worse(child + 1, child)) child++;
            if (!worse(child, parent)) break;
            swap(parent, child); parent = child;
        }
    }

    private boolean worse(int a, int b) {
        return distances[a] > distances[b] || distances[a] == distances[b] && order[a] > order[b];
    }

    private void swap(int a, int b) {
        Object value = values[a]; values[a] = values[b]; values[b] = value;
        double distance = distances[a]; distances[a] = distances[b]; distances[b] = distance;
        long arrival = order[a]; order[a] = order[b]; order[b] = arrival;
    }

    public int size() { return size; }

    @SuppressWarnings("unchecked") // Only values of T enter this private array through offer.
    public T valueAt(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        return (T) values[index];
    }

    public void clear() {
        Arrays.fill(values, 0, size, null);
        size = 0; sequence = 0;
    }
}
