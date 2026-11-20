package dev.dynamicchunkie.visual;

import net.minecraft.client.CloudStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudGovernorTest {
    @Test void transientLoadAndBriefRecoveryDoNotSwitchQuality() {
        var governor = new CloudGovernor();
        governor.update(.4, 0);
        governor.update(1, 900_000_000L);
        assertEquals(CloudStatus.FANCY, governor.limit(CloudStatus.FANCY));
        governor.update(.4, 1_000_000_000L);
        governor.update(.4, 2_000_000_000L);
        assertEquals(CloudStatus.FAST, governor.limit(CloudStatus.FANCY));
        governor.update(1, 3_000_000_000L);
        governor.update(1, 7_999_999_999L);
        assertEquals(CloudStatus.FAST, governor.limit(CloudStatus.FANCY));
        governor.update(1, 8_000_000_000L);
        assertEquals(CloudStatus.FANCY, governor.limit(CloudStatus.FANCY));
        assertEquals(CloudStatus.OFF, governor.limit(CloudStatus.OFF));
    }
}
