package dev.dynamicchunkie.mixin.sodium;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo @Mixin(value=ChunkRenderList.class,remap=false)
public interface SodiumSectionCandidatesAccess {
    @Accessor("sectionsWithGeometry") byte[] dynamicchunkie$geometry();
}
