package dev.dynamicchunkie.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.dynamicchunkie.terrain.TerrainCulling;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
/** Filter the draw input only; the native visible list, compile queues and meshes stay intact. */
@Mixin(LevelRenderer.class)
abstract class TerrainDrawMixin {
    @Unique private final ObjectArrayList<RenderSection> dynamicchunkie$drawSections=new ObjectArrayList<>();
    @WrapOperation(method="extractSectionDrawGroups",at=@At(value="FIELD",opcode=Opcodes.GETFIELD,
            target="Lnet/minecraft/client/renderer/LevelRenderer;visibleSections:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<RenderSection> dynamicchunkie$visible(LevelRenderer renderer,Operation<ObjectArrayList<RenderSection>> original) {
        var source=original.call(renderer);TerrainCulling.hook();
        dev.dynamicchunkie.sections.HotSoftSections.verifyView();
        if(!TerrainCulling.active() && !dev.dynamicchunkie.sections.HotSoftSections.filtering()) {
            if(TerrainCulling.metricsEnabled())for(RenderSection section:source)if(section.getSectionMesh().hasRenderableLayers()) {
                var pos=section.getRenderOrigin();TerrainCulling.candidate(pos.getX(),pos.getY(),pos.getZ());
            }
            return source;
        }
        dynamicchunkie$drawSections.clear();
        for(RenderSection section:source) {
            var pos=section.getRenderOrigin();
            boolean geometry=section.getSectionMesh().hasRenderableLayers();
            if(geometry)TerrainCulling.candidate(pos.getX(),pos.getY(),pos.getZ());
            if(!geometry || (!TerrainCulling.hide(pos.getX(),pos.getY(),pos.getZ()) && dev.dynamicchunkie.sections.HotSoftSections.drawAllowed(pos.getX(),pos.getY(),pos.getZ())))
                dynamicchunkie$drawSections.add(section);
        }
        return dynamicchunkie$drawSections;
    }
}
