package dev.dynamicchunkie;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigCopyTest {
    @Test void editsStayInDraftUntilApplied() {
        var original = new DynamicChunkieConfig();
        var draft = original.copy();
        draft.minRenderDistance = 6;
        draft.enabled = false;
        assertEquals(2, original.minRenderDistance);
        assertTrue(original.enabled);
        original.copySettingsFrom(draft);
        assertEquals(6, original.minRenderDistance);
        assertFalse(original.enabled);
    }
    @Test void applyingUiSettingsPreservesRestorePoint() {
        var original = new DynamicChunkieConfig();
        original.defaultRenderDistance = 18;
        original.restorePending = true;
        original.copySettingsFrom(new DynamicChunkieConfig());
        assertEquals(18, original.defaultRenderDistance);
        assertTrue(original.restorePending);
    }
    @Test void draftCopyIncludesAllProfileAndTimingSettings() {
        var original = new DynamicChunkieConfig();
        original.targetStabilityMs = 4000;
        original.preserveRenderCache = false;
        original.highAltitudeDistance = 20;
        original.smoothingFactor = .5;
        var copy = original.copy();
        assertEquals(4000, copy.targetStabilityMs);
        assertFalse(copy.preserveRenderCache);
        assertEquals(20, copy.highAltitudeDistance);
        assertEquals(.5, copy.smoothingFactor);
    }
}
