package dev.dynamicchunkie.mixin;
import dev.dynamicchunkie.visual.ParticlePolicy;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ParticleEngine.class)
abstract class ParticleBudgetMixin {
    @Inject(method="createParticle",at=@At("HEAD"),cancellable=true)
    private void dynamicchunkie$budget(ParticleOptions options,double x,double y,double z,
            double dx,double dy,double dz,CallbackInfoReturnable<Particle> cir) {
        if(ParticlePolicy.reject(options,x,y,z))cir.setReturnValue(null);
    }
}
