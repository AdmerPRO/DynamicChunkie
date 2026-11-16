package dev.dynamicchunkie.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class CompatibilityMixinPlugin implements IMixinConfigPlugin {
    public static boolean supportedSodium() {
        return FabricLoader.getInstance().getModContainer("sodium")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("0.7.3+mc1.21.10"))
                .orElse(false);
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!mixin.contains(".sodium.") && (mixin.endsWith("LevelRendererMixin") || mixin.endsWith("ViewAreaMixin"))
                && FabricLoader.getInstance().isModLoaded("sodium")) return false;
        return !mixin.contains(".sodium.") || supportedSodium();
    }
    @Override public void onLoad(String pkg) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
