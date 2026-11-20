package dev.dynamicchunkie.mixin;
import dev.dynamicchunkie.DynamicChunkie;
import dev.dynamicchunkie.visual.EntityVisualPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityDistanceMixin {
    @Inject(method="tryExtractRenderState",at=@At("HEAD"),cancellable=true)
    private void dynamicchunkie$distance(BlockEntity block,float tick,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay overlay,
            boolean flag,CallbackInfoReturnable<?> cir) {
        var c=DynamicChunkie.settings();
        if(c==null || !c.enabled)return;
        var pos=block.getBlockPos();
        // Exact built-in types only. The section proof includes two blocks of overhang.
        if (c.enableTerrainSectionCulling && dev.dynamicchunkie.terrain.TerrainCulling.active()) {
            var renderer=((BlockEntityRenderDispatcher)(Object)this).getRenderer(block);
            Class<?> type=renderer==null?null:renderer.getClass();
            if ((type==net.minecraft.client.renderer.blockentity.ChestRenderer.class ||
                    type==net.minecraft.client.renderer.blockentity.StandingSignRenderer.class ||
                    type==net.minecraft.client.renderer.blockentity.HangingSignRenderer.class) &&
                    dev.dynamicchunkie.terrain.TerrainCulling.hide(pos.getX()&~15,pos.getY()&~15,pos.getZ()&~15)) {
                cir.setReturnValue(null);
                return;
            }
        }
        if(!c.enableAdaptiveEntityDistance)return;
        double limit=EntityVisualPolicy.blockDistance();
        var camera=Minecraft.getInstance().gameRenderer.mainCamera().position();
        if(camera.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>limit*limit)cir.setReturnValue(null);
    }
}
