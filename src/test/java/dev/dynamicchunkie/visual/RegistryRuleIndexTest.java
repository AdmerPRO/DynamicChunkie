package dev.dynamicchunkie.visual;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegistryRuleIndexTest {
    @Test void resolvesEachTypeOnceAndPreservesExplicitZero() {
        var calls = new AtomicInteger();
        var rules = new RegistryRuleIndex<String>(type -> { calls.incrementAndGet(); return type; });
        rules.configure(Map.of("smoke",0), Set.of("crit"));
        String smoke = new String("smoke");
        for (int i = 0; i < 1000; i++) assertEquals(0, rules.rule(smoke).limit());
        assertEquals(1, calls.get());
        assertTrue(rules.rule("crit").important()); assertEquals(-1, rules.rule("unknown").limit());
    }
    @Test void applyingSettingsInvalidatesResolutionAndSnapshotsMutableMaps() {
        var limits = new HashMap<String,Integer>(); limits.put("cow",2);
        var rules = new RegistryRuleIndex<String>(type -> type);
        rules.configure(limits, Set.of()); assertEquals(2, rules.rule("cow").limit());
        limits.put("cow",5); assertEquals(2, rules.rule("cow").limit());
        rules.configure(limits, Set.of("cow"));
        assertEquals(5, rules.rule("cow").limit()); assertTrue(rules.rule("cow").important());
    }
}
