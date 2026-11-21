package dev.dynamicchunkie.mixin;

import dev.dynamicchunkie.sections.HotSoftSections;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSection.class)
abstract class VanillaMeshReadyMixin implements dev.dynamicchunkie.render.SectionMeshCost {
    @org.spongepowered.asm.mixin.Unique private long dynamicchunkie$indices=-2;
    @org.spongepowered.asm.mixin.Unique private static final net.minecraft.client.renderer.chunk.ChunkSectionLayer[] dynamicchunkie$layers =
            net.minecraft.client.renderer.chunk.ChunkSectionLayer.values();

    @SuppressWarnings("PMD.CloseResource") // The renderer owns the published mesh.
    public long dynamicchunkie$geometryCost() {
        if (dynamicchunkie$indices!=-2) return dynamicchunkie$indices;
        var mesh=((RenderSection)(Object)this).getSectionMesh();
        long indices=0;
        if (mesh.hasRenderableLayers()) for (var layer:dynamicchunkie$layers) {
            var draw=mesh.getSectionDraw(layer);
            if (draw!=null) indices+=draw.indexCount();
        }
        return dynamicchunkie$indices=indices==0?-1:indices;
    }

    @Inject(method="reset",at=@At("HEAD"))
    private void dynamicchunkie$reset(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dynamicchunkie$indices=-2;
    }

    @Inject(method="setSectionMesh",at=@At("RETURN"))
    private void dynamicchunkie$published(SectionMesh mesh, CallbackInfoReturnable<SectionMesh> result) {
        dynamicchunkie$indices=-2;
        var pos=((RenderSection)(Object)this).getRenderOrigin();
        HotSoftSections.meshCompleted(pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4);
    }
}
