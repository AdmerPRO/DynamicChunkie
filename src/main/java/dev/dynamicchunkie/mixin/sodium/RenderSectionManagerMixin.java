package dev.dynamicchunkie.mixin.sodium;

import dev.dynamicchunkie.compat.SodiumDistanceAccess;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(value = RenderSectionManager.class, remap = false)
abstract class RenderSectionManagerMixin implements SodiumDistanceAccess {
    @Shadow @Final @Mutable private int renderDistance;
    @Shadow @Final private it.unimi.dsi.fastutil.longs.Long2ReferenceMap<net.caffeinemc.mods.sodium.client.render.chunk.RenderSection> sectionByPosition;
    @Shadow public abstract void markGraphDirty();

    @Override public Object dynamicchunkie$sectionAt(int x, int y, int z) {
        return sectionByPosition.get(net.minecraft.core.SectionPos.asLong(x, y, z));
    }
    @Override public void dynamicchunkie$setDistance(int radius) {
        renderDistance = radius;
        // Sodium already supports growing its section tree without discarding regions.
        markGraphDirty();
    }
}
