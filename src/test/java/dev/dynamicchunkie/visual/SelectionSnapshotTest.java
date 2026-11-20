package dev.dynamicchunkie.visual;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectionSnapshotTest {
    @Test void immutableInputAndWorldCameraStampProtectResults() {
        var stamp = new SelectionSnapshot.Stamp(1,3,0,0,0);
        var items = new ArrayList<SelectionSnapshot.Candidate>();
        items.add(new SelectionSnapshot.Candidate(1,0,1,40));
        items.add(new SelectionSnapshot.Candidate(2,0,1,4));
        var snapshot = new SelectionSnapshot(stamp, items);
        items.clear();
        var result = snapshot.select();
        assertEquals(1,result.hidden().size());
        assertTrue(result.hidden().contains(1));
        assertTrue(result.validFor(stamp));
        assertFalse(result.validFor(new SelectionSnapshot.Stamp(1,3,0,1,0)));
        assertFalse(result.validFor(new SelectionSnapshot.Stamp(2,3,0,0,0)));
    }
}
