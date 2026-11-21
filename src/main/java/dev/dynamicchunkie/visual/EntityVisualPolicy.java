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
    private static final IntOpenHashSet distant = new IntOpenHashSet();
    private static final IntOpenHashSet hidden = new IntOpenHashSet();
    private static final IdentityHashMap<EntityType<?>, NearestSelection<Entity>> selections = new IdentityHashMap<>();
    private static Object world;
    private static long observedSkips, generation;
    private static java.util.concurrent.CompletableFuture<SelectionSnapshot.Result> pending;
    private static SelectionSnapshot.Stamp applied;
    private static long membershipRevision;
    public static void costChanged() {membershipRevision++;selections.clear();}
    public static void entityChanged(Entity entity) {
        var c=DynamicChunkie.settings();
        if (c!=null && c.enabled && c.enableEntityTypeLimits && !c.entityTypeLimits.isEmpty() &&
                entity.level()==world && VisualRules.entity(entity.getType()).limit()>0) membershipRevision++;
    }
    private static final net.minecraft.world.level.entity.EntityTypeTest<Entity,Entity> ALL_ENTITIES =
            net.minecraft.world.level.entity.EntityTypeTest.forClass(Entity.class);
    private static Iterable<Entity> candidates(Minecraft client,int maximum) {
        var c=DynamicChunkie.settings();
        if (!c.enableAdaptiveEntityDistance) return client.level.entitiesForRendering();
        var camera=client.gameRenderer.mainCamera().position();
        double radius=Math.max(c.mobVisualDistance,Math.max(c.itemVisualDistance,c.armorStandVisualDistance))+4;
        var result=new java.util.ArrayList<Entity>();
        client.level.getEntities(ALL_ENTITIES,new net.minecraft.world.phys.AABB(camera,camera).inflate(radius),
                entity->!(entity instanceof Player) && VisualRules.entity(entity.getType()).limit()>0,result,maximum+1);
        return result;
    }
    private static final CloudGovernor clouds = new CloudGovernor();
    public static net.minecraft.client.CloudStatus clouds(net.minecraft.client.CloudStatus selected) {return clouds.limit(selected);}
    private static double scale=1, particleScale=1;
    public static double particleScale() {return particleScale;}

    private EntityVisualPolicy() {}

    public static void invalidate() {
        generation++;
        applied = null;
        distant.clear(); hidden.clear(); selections.clear(); world = null; observedSkips = 0; scale=particleScale=1; clouds.reset();
    }

    public static void forget(int entityId) {
        distant.remove(entityId);
        hidden.remove(entityId);
        if (pending != null) generation++;
    }

    public static void release() {
        invalidate();
        hidden.trim();
        distant.trim();
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
            scale += (desired - scale) * (desired < scale ? .06 : .015);
            particleScale+=(desired-particleScale)*(desired<particleScale?.12:.025);
            if (c.enableSmartClouds) clouds.update(desired, System.nanoTime()); else clouds.reset();
        } else {scale=particleScale=1;clouds.reset();}
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
        if (applied!=null && applied.equals(stamp(client))) {
            WorkCoordinator.complete(AnalysisScheduler.Module.ENTITY_SELECTION,now,observedSkips);
            observedSkips=0;
            return;
        }
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
        boolean complete=true;
        for (Entity entity : candidates(client,c.entityMetadataLimitKiB*8)) {
            if (++scanned > c.entityMetadataLimitKiB * 8 || ((scanned & 63) == 0 && !WorkCoordinator.available())) {
                hidden.clear();
                complete=false;
                break;
            }
            if (entity instanceof Player || c.protectImportantEntities && (entity.isCurrentlyGlowing() || entity.hasCustomName())) continue;
            var type = entity.getType();
            int limit = EntityModelCosts.limit(type);
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
        if (complete) applied=stamp(client);
        WorkCoordinator.complete(AnalysisScheduler.Module.ENTITY_SELECTION, now, observedSkips);
        observedSkips = 0;
    }

    private static SelectionSnapshot.Stamp stamp(Minecraft client) {
        var camera = client.gameRenderer.mainCamera().position();
        return new SelectionSnapshot.Stamp(generation, membershipRevision, camera.x, camera.y, camera.z);
    }

    private static void scheduleSnapshot(Minecraft client) {
        var settings = DynamicChunkie.settings();
        var camera = client.gameRenderer.mainCamera().position();
        var inputs = new java.util.ArrayList<SelectionSnapshot.Candidate>();
        int scanned = 0;
        for (var entity : candidates(client,settings.entityMetadataLimitKiB*2)) {
            if ((++scanned & 63) == 0 && !WorkCoordinator.available() ||
                    inputs.size() >= settings.entityMetadataLimitKiB * 2) {
                hidden.clear();
                return;
            }
            if (entity instanceof Player || settings.protectImportantEntities &&
                    (entity.hasCustomName() || entity.isCurrentlyGlowing())) continue;
            int limit = EntityModelCosts.limit(entity.getType());
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
        int id=entity.getId();
        double threshold=distant.contains(id)?Math.max(0,distance-2):distance;
        if (entity.distanceToSqr(x,y,z)>threshold*threshold) {
            if (distant.size()<c.entityMetadataLimitKiB*16) distant.add(id);
            return Reason.DISTANCE;
        }
        distant.remove(id);
        return null;
    }

    public static double scale() { return scale; }
    public static double blockDistance() { return DynamicChunkie.settings().blockEntityVisualDistance * scale; }
}
