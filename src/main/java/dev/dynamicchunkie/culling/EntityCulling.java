package dev.dynamicchunkie.culling;

import dev.dynamicchunkie.runtime.WorkCoordinator;
import dev.dynamicchunkie.runtime.AnalysisScheduler;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.dynamicchunkie.DynamicChunkie;

/**
 * CPU visibility estimates only. No entities, ticks, networking or world data are removed.
 * Ray work is budgeted on client ticks; the render hook only reads cached decisions.
 */
public final class EntityCulling {
    private static final long MAX_AGE = 500_000_000L;
    private final boolean externalCuller = FabricLoader.getInstance().isModLoaded("entityculling");
    private IdentityHashMap<Entity, State> states = new IdentityHashMap<>();
    private Set<Entity> scheduled=Collections.newSetFromMap(new IdentityHashMap<>());
    private ArrayDeque<Entity> queue = new ArrayDeque<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final VoxelOcclusion.Blocks blocks = this::blockAt;
    private net.minecraft.client.multiplayer.ClientLevel world;
    private long snapshotAt, observedSkips;
    private static final class State {
        Vec3 camera, position;
        AABB box;
        long checkedAt;
        int blockedSamples;
    }
    public void clear() { states.clear(); queue.clear(); scheduled.clear(); world = null; snapshotAt = 0; }
    public void release() {
        clear();
        states = new IdentityHashMap<>();
        scheduled = Collections.newSetFromMap(new IdentityHashMap<>());
        queue = new ArrayDeque<>();
        observedSkips = 0;
    }
    public void forget(Entity entity) {
        if (scheduled.remove(entity)) queue.remove(entity);
        states.remove(entity);
    }

    public boolean enabled() { var config=DynamicChunkie.settings(); return config != null && config.enabled && config.enableEntityCulling && !externalCuller; }

    public void tick(Minecraft client) {
        if (!enabled() || client.level == null || client.player == null) { clear(); return; }
        if (world != client.level) { clear(); world = client.level; }
        if (!WorkCoordinator.admit(AnalysisScheduler.Module.ENTITY_OCCLUSION, 50_000_000L, false)) return;
        var config=DynamicChunkie.settings();
        long now = System.nanoTime();
        if (snapshotAt == 0 || now - snapshotAt > 500_000_000L) {
            queue.clear();scheduled.clear();
            int scanned = 0;
            for (Entity entity : world.entitiesForRendering()) {
                if ((++scanned & 63) == 0 && !WorkCoordinator.available()) break;
                if (candidate(entity, client)) {queue.addLast(entity);scheduled.add(entity);}
                if (queue.size() >= Math.min(2048, config.entityMetadataLimitKiB * 4)) break;
            }
            states.keySet().removeIf(entity -> !scheduled.contains(entity));
            snapshotAt = now;
        }
        Vec3 camera = client.gameRenderer.mainCamera().position();
        int budget=config.entityCullingBudget;
        if (config.enableHardwareAdaptive && dev.dynamicchunkie.DynamicChunkie.distanceManager().performance().cpuPressure())
            budget=Math.max(1,budget/2);
        int count = Math.min(budget, queue.size());
        for (int i = 0; i < count && WorkCoordinator.available(); i++) {
            Entity entity = queue.removeFirst();
            if (!entity.isRemoved()) {
                queue.addLast(entity);
                sample(entity, camera, now);
            } else states.remove(entity);
        }
        WorkCoordinator.complete(AnalysisScheduler.Module.ENTITY_OCCLUSION, now, observedSkips);
        observedSkips=0;
    }

    private boolean candidate(Entity entity, Minecraft client) {
        // Players/name tags, unusual geometry, mounted entities and near objects stay visible.
        AABB box = entity.getBoundingBox();
        return entity != client.player && !(entity instanceof Player) &&
                (entity instanceof LivingEntity || entity instanceof ItemEntity) &&
                !entity.isRemoved() && !entity.isVehicle() && !entity.isPassenger() &&
                !entity.hasCustomName() && !entity.isCurrentlyGlowing() &&
                box.getXsize() <= 4 && box.getYsize() <= 4 && box.getZsize() <= 4;
    }
    private void sample(Entity entity, Vec3 camera, long now) {
        State state = states.computeIfAbsent(entity, ignored -> new State());
        Vec3 position = entity.position();
        if (state.camera == null || state.camera.distanceToSqr(camera) > .0625 ||
                state.position.distanceToSqr(position) > .0625) state.blockedSamples = 0;
        double distance = entity.distanceToSqr(camera);
        var config=DynamicChunkie.settings();
        boolean blocked = distance >= 64 && distance <= config.entityCullingDistance * config.entityCullingDistance;
        AABB box = entity.getBoundingBox().inflate(.25);
        if (blocked) blocked = VoxelOcclusion.blocked(camera.x,camera.y,camera.z,
                (box.minX+box.maxX)*.5,(box.minY+box.maxY)*.5,(box.minZ+box.maxZ)*.5,blocks);
        if (blocked) {
            for (int corner = 0; corner < 8; corner++) {
                double x = (corner & 1) == 0 ? box.minX : box.maxX;
                double y = (corner & 2) == 0 ? box.minY : box.maxY;
                double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
                if (!VoxelOcclusion.blocked(camera.x, camera.y, camera.z, x, y, z, blocks)) { blocked = false; break; }
            }
        }
        state.blockedSamples = blocked ? Math.min(2, state.blockedSamples + 1) : 0;
        state.camera = camera;
        state.position = position;
        state.box = entity.getBoundingBox();
        state.checkedAt = now;
    }
    private int blockAt(int x, int y, int z) {
        if (!world.getChunkSource().hasChunk(x >> 4, z >> 4)) return -1;
        cursor.set(x, y, z);
        return world.getBlockState(cursor).isSolidRender() ? 1 : 0;
    }
    public boolean shouldCull(Entity entity, double cameraX, double cameraY, double cameraZ) {
        if (!enabled() || world == null || !candidate(entity, Minecraft.getInstance())) return false;
        State state = states.get(entity);
        if (state == null || state.blockedSamples < 2 || System.nanoTime() - state.checkedAt > MAX_AGE) return false;
        if (!state.box.equals(entity.getBoundingBox())) return false;
        if (state.camera.distanceToSqr(cameraX, cameraY, cameraZ) > .015625) return false;
        observedSkips++;
        return true;
    }
}
