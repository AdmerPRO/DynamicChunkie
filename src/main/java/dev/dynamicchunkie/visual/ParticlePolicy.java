package dev.dynamicchunkie.visual;
import dev.dynamicchunkie.DynamicChunkie;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
/** Spawn budgets for cosmetic effects. Important effects bypass the cosmetic budget. */
public final class ParticlePolicy {
    private static final Map<String,Integer> counts=new HashMap<>();
    private static int total, active;
    private ParticlePolicy() {}
    public static void tick(Minecraft client) {
        counts.clear();total=0;active=0;
        if(client.level!=null && client.particleEngine!=null) {
            try {active=Integer.parseInt(client.particleEngine.countParticles());}
            catch(NumberFormatException ignored) {active=0;}
        }
    }
    public static boolean reject(ParticleOptions options,double x,double y,double z) {
        var c=DynamicChunkie.configOrNull();var client=Minecraft.getInstance();
        if(c==null || !c.enabled || !c.enableAdaptiveParticles || client.level==null)return false;
        String id=BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).toString();
        if(options.getType().getOverrideLimiter() || c.importantParticleTypes.contains(id))return false;
        double factor=EntityVisualPolicy.scale();
        double radius=c.particleVisualDistance*factor;
        if(client.gameRenderer.mainCamera().position().distanceToSqr(x,y,z)>radius*radius)return true;
        int limit=c.particleTypeBudgets.getOrDefault(id,c.particleSpawnBudget);
        if(limit==0)return true;
        int used=counts.getOrDefault(id,0);
        if(active+total>=c.particleActiveLimit || total>=Math.max(1,(int)(c.particleSpawnBudget*factor)) ||
                used>=Math.max(1,(int)(limit*factor)))return true;
        total++;counts.put(id,used+1);return false;
    }
}
