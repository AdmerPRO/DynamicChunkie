package dev.dynamicchunkie.visual;

import dev.dynamicchunkie.DynamicChunkie;
import java.util.IdentityHashMap;
import net.minecraft.world.entity.EntityType;

/** Sparse client CPU submission samples. A budget applies to types with an explicit visual limit. */
public final class EntityModelCosts {
    private static final class Sample {
        int calls, observations;
        double nanos;
    }
    private static final IdentityHashMap<EntityType<?>,Sample> costs = new IdentityHashMap<>();
    private EntityModelCosts() {}
    public static void clear() {costs.clear();}
    public static long begin(EntityType<?> type) {
        var c=DynamicChunkie.settings();
        if (c==null || !c.enabled || !c.enableEntityTypeLimits || c.entityTypeCpuBudgetMicros==0 ||
                VisualRules.entity(type).limit()<=0) return 0;
        var sample=costs.computeIfAbsent(type,ignored->new Sample());
        return (sample.calls++ & 31)==0 ? System.nanoTime() : 0;
    }
    public static void finish(EntityType<?> type,long started) {
        if (started==0) return;
        var sample=costs.get(type);
        if (sample==null) return;
        int before=limit(type);
        double nanos=Math.max(1,System.nanoTime()-started);
        sample.nanos=sample.observations==0?nanos:sample.nanos*.9+nanos*.1;
        sample.observations++;
        if (before!=limit(type)) EntityVisualPolicy.costChanged();
    }
    public static boolean expensive(EntityType<?> type) {
        var sample=costs.get(type);
        return sample!=null && sample.observations>=8 && sample.nanos>50_000;
    }
    public static int limit(EntityType<?> type) {
        int maximum=VisualRules.entity(type).limit();
        int micros=DynamicChunkie.settings().entityTypeCpuBudgetMicros;
        var sample=costs.get(type);
        if (micros==0 || maximum<=0 || sample==null || sample.observations<8) return maximum;
        return Math.max(1,Math.min(maximum,(int)(micros*1000.0/sample.nanos)));
    }
}
