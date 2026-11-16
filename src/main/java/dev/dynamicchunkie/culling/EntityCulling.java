package dev.dynamicchunkie.culling;

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
import dev.dynamicchunkie.DynamicChunkieConfig;

/**
 * CPU visibility estimates only. No entities, ticks, networking or world data are removed.
 * Ray work is budgeted on client ticks; the render hook only reads cached decisions.
 */
public final class EntityCulling {
    private static final long MAX_AGE = 500_000_000L;
    private final DynamicChunkieConfig config;
    private final boolean externalCuller = FabricLoader.getInstance().isModLoaded("entityculling");
    private final IdentityHashMap<Entity, State> states = new IdentityHashMap<>();
    private final ArrayDeque<Entity> queue = new ArrayDeque<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final VoxelOcclusion.Blocks blocks = this::blockAt;
    private net.minecraft.client.multiplayer.ClientLevel world;
    private long snapshotAt;
    private int hidden;
    private static final class State {
        Vec3 camera, position;
        AABB box;
        long checkedAt;
        int blockedSamples;
    }
    public EntityCulling(DynamicChunkieConfig config) { this.config = config; }
    public void clear() { states.clear(); queue.clear(); world = null; hidden = 0; snapshotAt = 0; }
    public boolean enabled() { return config.enabled && config.enableEntityCulling && !externalCuller; }
    public int cachedHidden() { return hidden; }

    public void tick(Minecraft client) {
        if (!enabled() || client.level == null || client.player == null) { clear(); return; }
        if (world != client.level) { clear(); world = client.level; }
        long now = System.nanoTime();
        if (now - snapshotAt > 500_000_000L || queue.isEmpty()) {
            queue.clear();
            for (Entity entity : world.entitiesForRendering()) {
                if (candidate(entity, client)) queue.addLast(entity);
                if (queue.size() >= 2048) break;
            }
            states.keySet().removeIf(entity -> entity.isRemoved() || !candidate(entity, client));
            snapshotAt = now;
        }
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        int count = Math.min(config.entityCullingBudget, queue.size());
        for (int i = 0; i < count; i++) {
            Entity entity = queue.removeFirst();
            if (!entity.isRemoved()) {
                queue.addLast(entity);
                sample(entity, camera, now);
            } else states.remove(entity);
        }
        hidden = 0;
        for (State state : states.values())
            if (state.blockedSamples >= 2 && now - state.checkedAt < MAX_AGE) hidden++;
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
        boolean blocked = distance >= 64 && distance <= config.entityCullingDistance * config.entityCullingDistance;
        AABB box = entity.getBoundingBox().inflate(.25);
        if (blocked) blocked = ray(camera, box.getCenter());
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
    private boolean ray(Vec3 start, Vec3 end) {
        return VoxelOcclusion.blocked(start.x, start.y, start.z, end.x, end.y, end.z, blocks);
    }
    private int blockAt(int x, int y, int z) {
        if (!world.getChunkSource().hasChunk(x >> 4, z >> 4)) return -1;
        cursor.set(x, y, z);
        return world.getBlockState(cursor).isSolidRender(world, cursor) ? 1 : 0;
    }
    public boolean shouldCull(Entity entity, double cameraX, double cameraY, double cameraZ) {
        if (!enabled() || world == null || !candidate(entity, Minecraft.getInstance())) return false;
        State state = states.get(entity);
        if (state == null || state.blockedSamples < 2 || System.nanoTime() - state.checkedAt > MAX_AGE) return false;
        if (!state.box.equals(entity.getBoundingBox())) return false;
        return state.camera.distanceToSqr(cameraX, cameraY, cameraZ) <= .015625;
    }
}
