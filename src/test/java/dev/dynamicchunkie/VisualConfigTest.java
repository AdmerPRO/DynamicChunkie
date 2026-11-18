package dev.dynamicchunkie;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VisualConfigTest {
    @Test void draftsDoNotMutateLiveTypeBudgets() {
        var live=new DynamicChunkieConfig();live.entityTypeLimits.put("minecraft:cow",100);
        live.particleTypeBudgets.put("minecraft:smoke",20);
        var draft=live.copy();draft.entityTypeLimits.put("minecraft:cow",5);
        draft.particleTypeBudgets.put("minecraft:smoke",2);draft.importantParticleTypes.clear();
        assertEquals(100,live.entityTypeLimits.get("minecraft:cow"));
        assertEquals(20,live.particleTypeBudgets.get("minecraft:smoke"));
        assertFalse(live.importantParticleTypes.isEmpty());
    }
    @Test void malformedVisualSettingsAreBounded() {
        var c=new DynamicChunkieConfig();c.mobVisualDistance=-1;c.playerVisualDistance=100000;
        c.particleActiveLimit=-1;c.entityTypeLimits.put("minecraft:cow",-8);
        c.particleTypeBudgets.put("minecraft:smoke",-1);c.validate();
        assertEquals(8,c.mobVisualDistance);assertEquals(1024,c.playerVisualDistance);
        assertEquals(100,c.particleActiveLimit);assertEquals(0,c.entityTypeLimits.get("minecraft:cow"));
        assertEquals(1,c.particleTypeBudgets.get("minecraft:smoke"));
    }
}
