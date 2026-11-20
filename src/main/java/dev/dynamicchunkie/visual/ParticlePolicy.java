package dev.dynamicchunkie.visual;

import dev.dynamicchunkie.DynamicChunkie;
import dev.dynamicchunkie.mixin.FrustumBoundsAccess;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Client-side admission before the particle provider allocates a cosmetic effect. */
public final class ParticlePolicy {
    private static final Reference2IntOpenHashMap<ParticleType<?>> counts = new Reference2IntOpenHashMap<>();
    private static final Long2IntOpenHashMap regions = new Long2IntOpenHashMap();
    private static final int[] groups = new int[4];
    private static int total, active, demand, previousDemand, phase;
    private static double lastX, lastY, lastZ, lastDistance;
    private static Vec3 lastCamera;

    private ParticlePolicy() {}

    private static void clearTick() {
        counts.clear();
        regions.clear();
        java.util.Arrays.fill(groups, 0);
        total = active = demand = 0;
    }

    public static void invalidate() {
        clearTick();
        previousDemand = phase = 0;
        lastCamera = null;
    }

    public static void tick(Minecraft client) {
        previousDemand = demand;
        clearTick();
        phase++;
        var c = DynamicChunkie.settings();
        if (c == null || !c.enabled || !c.enableAdaptiveParticles || client.level == null || client.particleEngine == null) return;
        active = ((ParticleActivity) client.particleEngine).dynamicchunkie$activeCount();
    }

    private static boolean combat(ParticleType<?> type) {
        return type == ParticleTypes.CRIT || type == ParticleTypes.DAMAGE_INDICATOR ||
                type == ParticleTypes.ENCHANTED_HIT || type == ParticleTypes.SWEEP_ATTACK;
    }

    private static int group(ParticleType<?> type) {
        if (type == ParticleTypes.RAIN || type == ParticleTypes.SPLASH) return 1;
        if (type == ParticleTypes.HEART || type == ParticleTypes.HAPPY_VILLAGER) return 2;
        if (type == ParticleTypes.ASH || type == ParticleTypes.WHITE_ASH || type == ParticleTypes.SPORE_BLOSSOM_AIR ||
                type == ParticleTypes.DRIPPING_LAVA || type == ParticleTypes.DRIPPING_WATER) return 3;
        return 0;
    }

    private static double distance(Camera camera, double x, double y, double z) {
        Vec3 position = camera.position();
        if (position != lastCamera || x != lastX || y != lastY || z != lastZ) {
            lastCamera = position;
            lastX = x;
            lastY = y;
            lastZ = z;
            lastDistance = position.distanceToSqr(x,y,z);
        }
        return lastDistance;
    }

    public static void limitLifetime(ParticleOptions options, net.minecraft.client.particle.Particle particle,
                                     double x,double y,double z) {
        if (particle==null) return;
        var c=DynamicChunkie.settings();
        if (c==null || !c.enabled || !c.enableAdaptiveParticles || !c.shortenAmbientParticles ||
                EntityVisualPolicy.scale()>.8) return;
        var type=options.getType();
        if (type!=ParticleTypes.ASH && type!=ParticleTypes.WHITE_ASH && type!=ParticleTypes.SPORE_BLOSSOM_AIR) return;
        if (type.getOverrideLimiter() || VisualRules.particle(type).important()) return;
        double radius=c.particleVisualDistance*.6;
        if (distance(Minecraft.getInstance().gameRenderer.mainCamera(),x,y,z)>radius*radius)
            particle.setLifetime(Math.max(1,particle.getLifetime()/2));
    }

    public static boolean reject(ParticleOptions options, double x, double y, double z) {
        var c = DynamicChunkie.settings();
        var client = Minecraft.getInstance();
        if (c == null || !c.enabled || !c.enableAdaptiveParticles || client.level == null) return false;
        var type = options.getType();
        if (type.getOverrideLimiter() || combat(type)) return false;
        var rule = VisualRules.particle(type);
        if (rule.important()) return false;
        double factor = EntityVisualPolicy.scale();
        double radius = c.particleVisualDistance * factor;
        var camera = client.gameRenderer.mainCamera();
        double distance = distance(camera,x,y,z);
        if (distance > radius * radius) return true;
        // These native landing particles stay at their landing site.
        if ((type == ParticleTypes.LANDING_LAVA || type == ParticleTypes.LANDING_HONEY) && distance > 256 &&
                camera.getCullFrustum() != null && ((FrustumBoundsAccess)(Object)camera.getCullFrustum())
                .dynamicchunkie$bounds(x-2,y-2,z-2,x+2,y+2,z+2) >= 0) return true;
        int limit = rule.limit() < 0 ? c.particleSpawnBudget : rule.limit();
        if (limit == 0) return true;
        int budget = Math.max(1, (int)(c.particleSpawnBudget * factor));
        int index = demand++;
        int used = counts.getInt(type);
        if (active + total >= c.particleActiveLimit || total >= budget || used >= Math.max(1,(int)(limit * factor))) return true;
        if (previousDemand > budget && Math.floorMod((long)index * budget + phase, previousDemand) >= budget) return true;
        int group = group(type);
        long region = SectionPos.asLong((int)Math.floor(x)>>4,(int)Math.floor(y)>>4,(int)Math.floor(z)>>4);
        if (budget >= 8 && (regions.get(region) >= (budget*3+3)/4 || group != 0 && groups[group] >= (budget+1)/2)) return true;
        groups[group]++;
        regions.addTo(region, 1);
        counts.put(type, used + 1);
        total++;
        return false;
    }
}
