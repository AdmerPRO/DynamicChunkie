package dev.dynamicchunkie.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public interface SodiumRendererAccessor {
    @Accessor("renderSectionManager") RenderSectionManager dynamicchunkie$getManager();
}
