package dev.dynamicchunkie.mixin.sodium;

import java.util.List;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Retirement must leave a section alone until its native jobs and queued outputs have drained. */
@Pseudo
@Mixin(value = RenderSection.class, remap = false)
public interface SodiumMeshJobs {
    @Accessor("runningJobs") List<ChunkJob> dynamicchunkie$jobs();
    @Accessor("pendingBuildOutput") ChunkBuildOutput dynamicchunkie$buildOutput();
    @Accessor("pendingDynamicSortOutput") ChunkSortOutput dynamicchunkie$sortOutput();
}
