package dev.dynamicchunkie;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistanceOwnershipTest {
    @Test void failedAcquireAndManualChangesPreserveTheNativeSetting() {
        var journal = new DynamicChunkieConfig();
        var nativeValue = new AtomicInteger(19);
        var writable = new AtomicBoolean(false);
        var owner = new DistanceOwnership(journal, nativeValue::get, nativeValue::set, () -> {}, writable::get);
        assertFalse(owner.acquire());
        assertFalse(journal.restorePending);
        assertThrows(IllegalStateException.class, () -> owner.set(2));
        writable.set(true);
        assertTrue(owner.acquire());
        owner.set(2);
        nativeValue.set(21);
        assertTrue(owner.captureManualChange());
        owner.set(4);
        writable.set(false);
        assertFalse(owner.release());
        assertEquals(21, nativeValue.get());
        assertTrue(journal.restorePending);
        writable.set(true);
        assertTrue(owner.release());
        assertFalse(journal.restorePending);
    }
}
