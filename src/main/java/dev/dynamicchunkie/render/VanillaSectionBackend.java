package dev.dynamicchunkie.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

final class VanillaSectionBackend implements SectionBackend {
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    @Override public boolean collect(Visitor visitor) {
        var client = Minecraft.getInstance();
        if (client.levelRenderer.viewArea() == null) return false;
        for (var section : client.levelRenderer.visibleSections()) {
            var origin = section.getRenderOrigin();
            if (!visitor.visit(origin.getX(), origin.getY(), origin.getZ())) return false;
        }
        return true;
    }

    @Override public boolean hasBuiltMesh(int x, int y, int z) {
        var area = Minecraft.getInstance().levelRenderer.viewArea();
        var section = area == null ? null : area.getRenderSectionAt(cursor.set(x * 16, y * 16, z * 16));
        return section != null && section.getSectionMesh().hasRenderableLayers();
    }

    @Override public void requestRebuild(int x, int y, int z) {
        Minecraft.getInstance().levelExtractor.setSectionDirty(x, y, z);
    }
}
