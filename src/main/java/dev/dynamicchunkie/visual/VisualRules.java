package dev.dynamicchunkie.visual;

import dev.dynamicchunkie.DynamicChunkieConfig;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import java.util.Set;

/** Compiled visual-rule indexes. Replaced only after a validated configuration is successfully applied. */
public final class VisualRules {
    private static final RegistryRuleIndex<EntityType<?>> ENTITIES = new RegistryRuleIndex<>(
            type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    private static final RegistryRuleIndex<ParticleType<?>> PARTICLES = new RegistryRuleIndex<>(
            type -> BuiltInRegistries.PARTICLE_TYPE.getKey(type).toString());

    private VisualRules() {}

    public static void configure(DynamicChunkieConfig config) {
        ENTITIES.configure(config.entityTypeLimits, Set.of());
        PARTICLES.configure(config.particleTypeBudgets, config.importantParticleTypes);
        EntityVisualPolicy.invalidate();
        ParticlePolicy.invalidate();
    }

    public static RegistryRuleIndex.Rule entity(EntityType<?> type) { return ENTITIES.rule(type); }
    public static RegistryRuleIndex.Rule particle(ParticleType<?> type) { return PARTICLES.rule(type); }
}
