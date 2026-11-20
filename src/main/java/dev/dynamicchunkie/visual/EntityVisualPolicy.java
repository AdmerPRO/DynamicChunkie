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
    private static long observedSkips, generation;
    private static java.util.concurrent.CompletableFuture<SelectionSnapshot.Result> pending;
    private static SelectionSnapshot.Stamp applied;
    private static double scale = 1;

    private EntityVisualPolicy() {}

    public static void invalidate() {
        if (world != null) generation++;
        applied = null;
        hidden.clear(); selections.clear(); world = null; observedSkips = 0; scale = 1;
    }

    public static void forget(int entityId) {
        hidden.remove(entityId);
        if (pending != null) generation++;
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
        if (world != null && world != client.level) invalidate();
        if (applied != null && !applied.equals(stamp(client))) {
            hidden.clear();
            applied = null;
        }
        if (pending != null && pending.isDone()) {
            var result = pending.isCompletedExceptionally() ? null : pending.getNow(null);
            pending = null;
            if (world == client.level && c.enableAsyncSelection && result != null && result.validFor(stamp(client))) {
                hidden.clear();
                hidden.addAll(result.hidden());
                applied = result.stamp();
            }
        }
        long now = System.nanoTime();
        if (!WorkCoordinator.admit(AnalysisScheduler.Module.ENTITY_SELECTION, 250_000_000L, world != client.level)) return;
        world = client.level;
        if (c.enableAsyncSelection && c.enableEntityTypeLimits && !c.entityTypeLimits.isEmpty()) {
            if (pending == null) scheduleSnapshot(client);
            WorkCoordinator.complete(AnalysisScheduler.Module.ENTITY_SELECTION, now, observedSkips);
            observedSkips = 0;
            return;
        }
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

    private static SelectionSnapshot.Stamp stamp(Minecraft client) {
        var camera = client.gameRenderer.mainCamera().position();
        long geometry = ((dev.dynamicchunkie.EnvironmentRevision)client.level).dynamicchunkie$revision();
        return new SelectionSnapshot.Stamp(generation, geometry, camera.x, camera.y, camera.z);
    }

    private static void scheduleSnapshot(Minecraft client) {
        var settings = DynamicChunkie.settings();
        var camera = client.gameRenderer.mainCamera().position();
        var inputs = new java.util.ArrayList<SelectionSnapshot.Candidate>();
        int scanned = 0;
        for (var entity : client.level.entitiesForRendering()) {
            if ((++scanned & 63) == 0 && !WorkCoordinator.available() ||
                    inputs.size() >= settings.entityMetadataLimitKiB * 2) {
                hidden.clear();
                return;
            }
            if (entity instanceof Player || settings.protectImportantEntities &&
                    (entity.hasCustomName() || entity.isCurrentlyGlowing())) continue;
            int limit = VisualRules.entity(entity.getType()).limit();
            if (limit > 0) inputs.add(new SelectionSnapshot.Candidate(entity.getId(),
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getId(entity.getType()), limit,
                    entity.distanceToSqr(camera)));
        }
        var snapshot = new SelectionSnapshot(stamp(client), inputs);
        pending = java.util.concurrent.CompletableFuture.supplyAsync(snapshot::select);
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
