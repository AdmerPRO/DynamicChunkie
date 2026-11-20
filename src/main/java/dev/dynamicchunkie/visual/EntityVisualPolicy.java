package dev.dynamicchunkie.visual;

import dev.dynamicchunkie.runtime.WorkCoordinator;
import dev.dynamicchunkie.runtime.AnalysisScheduler;

import dev.dynamicchunkie.DynamicChunkie;
import dev.dynamicchunkie.diagnostics.EntityRejections.Reason;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/** Client-only selection. Never removes entities or changes their ticking/network state. */
public final class EntityVisualPolicy {
    private static final IntOpenHashSet hidden = new IntOpenHashSet();
    private static final IdentityHashMap<EntityType<?>, NearestSelection<Entity>> selections = new IdentityHashMap<>();
    private static Object world;
    private static long observedSkips;
    private static double scale = 1;

    private EntityVisualPolicy() {}

    public static void invalidate() {
        hidden.clear(); selections.clear(); world = null; observedSkips = 0; scale = 1;
    }

    public static void release() {
        invalidate();
        hidden.trim();
        dev.dynamicchunkie.diagnostics.EntityRejections.release();
    }

    public static void tick(Minecraft client) {
        var c = DynamicChunkie.settings();
        if (c == null || !c.enabled || client.level == null || client.player == null) {
            invalidate();
            return;
        }
        if (c.enableAdaptiveEntityDistance || c.enableAdaptiveParticles || c.enableSmartClouds) {
            double mean = DynamicChunkie.distanceManager().performance().meanMs();
            double desired = VisualBudget.scale(mean, c.targetFps, client.options.framerateLimit().get());
            scale += (desired - scale) * .025;
        } else scale = 1;
        long now = System.nanoTime();
        if (!WorkCoordinator.admit(AnalysisScheduler.Module.ENTITY_SELECTION, 250_000_000L, world != client.level)) return;
        world = client.level;
        hidden.clear();
        for (var selection : selections.values()) selection.clear();
        if (!c.enableEntityTypeLimits || c.entityTypeLimits.isEmpty()) return;
        var camera = client.gameRenderer.mainCamera().position();
        int scanned = 0;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (++scanned > c.entityMetadataLimitKiB * 8 || ((scanned & 63) == 0 && !WorkCoordinator.available())) {
                hidden.clear();
                break;
            }
            if (entity instanceof Player || c.protectImportantEntities && (entity.isCurrentlyGlowing() || entity.hasCustomName())) continue;
            var type = entity.getType();
            int limit = VisualRules.entity(type).limit();
            if (limit <= 0) continue;
            var selection = selections.get(type);
            if (selection == null) {
                selection = new NearestSelection<>(limit);
                selections.put(type, selection);
            }
            hidden.add(entity.getId());
            selection.offer(entity, entity.distanceToSqr(camera));
        }
        for (var selection : selections.values())
            for (int i = 0; i < selection.size(); i++) hidden.remove(selection.valueAt(i).getId());
        WorkCoordinator.complete(AnalysisScheduler.Module.ENTITY_SELECTION, now, observedSkips);
        observedSkips = 0;
    }

    public static boolean hide(Entity entity, double x, double y, double z) {
        return rejection(entity, x, y, z) != null;
    }

    public static Reason rejection(Entity entity, double x, double y, double z) {
        var c = DynamicChunkie.settings();
        if (c == null || !c.enabled) return null;
        var client = Minecraft.getInstance();
        if (entity == client.player || c.protectImportantEntities && (entity.isCurrentlyGlowing() || entity.hasCustomName())) return null;
        if (c.enableEntityTypeLimits && world == client.level && hidden.contains(entity.getId())) { observedSkips++; return Reason.TYPE_LIMIT; }
        if (!c.enableAdaptiveEntityDistance) return null;
        double distance = entity instanceof Player ? c.playerVisualDistance :
                entity instanceof ItemEntity ? c.itemVisualDistance :
                entity instanceof ArmorStand ? c.armorStandVisualDistance : c.mobVisualDistance;
        if (!(entity instanceof Player)) distance *= scale;
        return entity.distanceToSqr(x, y, z) > distance * distance ? Reason.DISTANCE : null;
    }

    public static double scale() { return scale; }
    public static double blockDistance() { return DynamicChunkie.settings().blockEntityVisualDistance * scale; }
}
