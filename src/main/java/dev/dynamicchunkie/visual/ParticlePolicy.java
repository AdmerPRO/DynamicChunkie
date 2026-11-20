package dev.dynamicchunkie.visual;

import dev.dynamicchunkie.DynamicChunkie;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/** Spawn budgets for cosmetic effects. Important effects bypass the cosmetic budget. */
public final class ParticlePolicy {
    private static final Reference2IntOpenHashMap<ParticleType<?>> counts = new Reference2IntOpenHashMap<>();
    private static int total, active;

    private ParticlePolicy() {}

    public static void invalidate() { counts.clear(); total = active = 0; }

    public static void tick(Minecraft client) {
        invalidate();
        var c = DynamicChunkie.settings();
        if (c == null || !c.enabled || !c.enableAdaptiveParticles || client.level == null || client.particleEngine == null) return;
        // Native API exposes the count as text. Parsing is confined to enabled client ticks.
        try { active = Integer.parseInt(client.particleEngine.countParticles()); }
        catch (NumberFormatException e) {
            // Unknown count is conservative: stop cosmetic admission until the next valid sample.
            active = c.particleActiveLimit;
        }
    }

    public static boolean reject(ParticleOptions options, double x, double y, double z) {
        var c = DynamicChunkie.settings();
        var client = Minecraft.getInstance();
        if (c == null || !c.enabled || !c.enableAdaptiveParticles || client.level == null) return false;
        var type = options.getType();
        if (type.getOverrideLimiter()) return false;
        var rule = VisualRules.particle(type);
        if (rule.important()) return false;
        double factor = EntityVisualPolicy.scale();
        double radius = c.particleVisualDistance * factor;
        if (client.gameRenderer.mainCamera().position().distanceToSqr(x, y, z) > radius * radius) return true;
        int limit = rule.limit() < 0 ? c.particleSpawnBudget : rule.limit();
        if (limit == 0) return true;
        int used = counts.getInt(type);
        if (active + total >= c.particleActiveLimit || total >= Math.max(1, (int) (c.particleSpawnBudget * factor)) ||
                used >= Math.max(1, (int) (limit * factor))) return true;
        total++; counts.put(type, used + 1);
        return false;
    }
}
