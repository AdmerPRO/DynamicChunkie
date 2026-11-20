package dev.dynamicchunkie.mixin;
import dev.dynamicchunkie.visual.ParticlePolicy;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ParticleEngine.class)
abstract class ParticleBudgetMixin implements dev.dynamicchunkie.visual.ParticleActivity {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private java.util.Map<ParticleRenderType, ParticleGroup<?>> particles;

    public int dynamicchunkie$activeCount() {
        int total = 0;
        for (var group : particles.values()) total += group.size();
        return total;
    }

    @Inject(method="createParticle",at=@At("RETURN"))
    private void dynamicchunkie$lifetime(ParticleOptions options,double x,double y,double z,
            double dx,double dy,double dz,CallbackInfoReturnable<Particle> cir) {
        ParticlePolicy.limitLifetime(options,cir.getReturnValue(),x,y,z);
    }
    @Inject(method="createParticle",at=@At("HEAD"),cancellable=true)
    private void dynamicchunkie$budget(ParticleOptions options,double x,double y,double z,
            double dx,double dy,double dz,CallbackInfoReturnable<Particle> cir) {
        if(ParticlePolicy.reject(options,x,y,z))cir.setReturnValue(null);
    }
}
