package dev.dynamicchunkie.visual;
import org.junit.jupiter.api.Test;
import net.minecraft.client.CloudStatus;
import static org.junit.jupiter.api.Assertions.*;
class VisualBudgetTest {
    @Test void fpsCapsDoNotReduceQualityAndHeavyFramesAreBounded() {
        assertEquals(1,VisualBudget.scale(1000.0/30,60,30));
        assertEquals(.35,VisualBudget.scale(100,60,120));
        assertEquals(1,VisualBudget.scale(Double.NaN,60,120));
    }
    @Test void cloudQualityDropsWithLoadAndNeverExceedsUserSetting() {
        assertEquals(CloudStatus.FAST,VisualBudget.clouds(CloudStatus.FANCY,.7));
        assertEquals(CloudStatus.OFF,VisualBudget.clouds(CloudStatus.FANCY,.4));
        assertEquals(CloudStatus.FAST,VisualBudget.clouds(CloudStatus.FAST,1));
        assertEquals(CloudStatus.OFF,VisualBudget.clouds(CloudStatus.OFF,1));
        assertEquals(CloudStatus.FANCY,VisualBudget.clouds(CloudStatus.FANCY,1));
    }
}
