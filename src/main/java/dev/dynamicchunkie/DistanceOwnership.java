package dev.dynamicchunkie;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Persist the user's distance before allowing automatic changes, including after a crash. */
final class DistanceOwnership {
    private final DynamicChunkieConfig journal;
    private final IntSupplier current;
    private final IntConsumer apply;
    private final Runnable saveOptions;
    private final BooleanSupplier saveJournal;
    private boolean owned;
    private int lastApplied;

    DistanceOwnership(DynamicChunkieConfig journal, IntSupplier current, IntConsumer apply,
                      Runnable saveOptions, BooleanSupplier saveJournal) {
        this.journal = journal;
        this.current = current;
        this.apply = apply;
        this.saveOptions = saveOptions;
        this.saveJournal = saveJournal;
    }

    boolean recover() {
        if (journal.restorePending && journal.defaultRenderDistance >= 2) {
            apply.accept(journal.defaultRenderDistance);
            saveOptions.run();
            journal.restorePending = false;
            if (!saveJournal.getAsBoolean()) {
                journal.restorePending = true;
                return false;
            }
        }
        return true;
    }

    boolean acquire() {
        if (owned) return true;
        int original = current.getAsInt();
        if (!persist(original)) return false;
        lastApplied = original;
        owned = true;
        return true;
    }

    boolean captureManualChange() {
        int value = current.getAsInt();
        if (!owned || value == lastApplied) return true;
        if (!persist(value)) return false;
        lastApplied = value;
        return true;
    }

    private boolean persist(int distance) {
        int previous = journal.defaultRenderDistance;
        boolean pending = journal.restorePending;
        journal.defaultRenderDistance = distance;
        journal.restorePending = true;
        if (saveJournal.getAsBoolean()) return true;
        journal.defaultRenderDistance = previous;
        journal.restorePending = pending;
        return false;
    }

    void set(int distance) {
        if (!owned) throw new IllegalStateException("Render distance is not owned");
        apply.accept(distance);
        lastApplied = current.getAsInt();
    }

    boolean release() {
        if (!owned) return true;
        if (!captureManualChange()) return false;
        if (!recover()) {
            lastApplied = current.getAsInt();
            return false;
        }
        owned = false;
        return true;
    }

    boolean owned() { return owned; }
}
