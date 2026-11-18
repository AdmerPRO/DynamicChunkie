package dev.dynamicchunkie.mixin;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Native bounds test without a temporary AABB allocation per section. */
@Mixin(Frustum.class)
public interface FrustumBoundsAccess {
    @Invoker("cubeInFrustum") int dynamicchunkie$bounds(double a,double b,double c,double d,double e,double f);
}
