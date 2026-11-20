package dev.dynamicchunkie.mixin.sodium;

import net.caffeinemc.mods.sodium.client.gpu.arena.BufferSegment;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value=SectionRenderDataStorage.class,remap=false)
public interface SodiumMeshAllocations {
    @Accessor("vertexAllocations") BufferSegment[] dynamicchunkie$vertices();
}
