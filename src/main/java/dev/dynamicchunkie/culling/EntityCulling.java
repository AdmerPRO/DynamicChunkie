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
    private ArrayDeque<Entity> priorityQueue = new ArrayDeque<>();
    private int turn;
    private ArrayDeque<Entity> queue = new ArrayDeque<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final VoxelOcclusion.Blocks blocks = this::blockAt;
    private net.minecraft.client.multiplayer.ClientLevel world;
    private long observedSkips, deadline;
    private final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap blockReads=new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
    private static final class State {
        Vec3 camera, position;
        AABB box;
        long checkedAt;
        int blockedSamples, firstCorner;
        long localRevision, globalRevision;
    }
    public void clear() { states.clear(); priorityQueue.clear(); queue.clear(); scheduled.clear(); world = null; blockReads.clear(); }
    public void release() {
        clear();
        states = new IdentityHashMap<>();
        scheduled = Collections.newSetFromMap(new IdentityHashMap<>());
        queue = new ArrayDeque<>();
        priorityQueue = new ArrayDeque<>();
        blockReads.trim();
        observedSkips = 0;
    }
    public void forget(Entity entity) {
        if (scheduled.remove(entity) && !priorityQueue.remove(entity)) queue.remove(entity);
        states.remove(entity);
    }

    public boolean enabled() { var config=DynamicChunkie.settings(); return config != null && config.enabled && config.enableEntityCulling && !externalCuller; }

    public void tick(Minecraft client) {
        if (!enabled() || client.level == null || client.player == null) { clear(); return; }
        if (world != client.level) { clear(); world = client.level; }
        boolean fastFlight = client.player.isFallFlying() && client.player.getDeltaMovement().lengthSqr()>1;
        if (!WorkCoordinator.admit(AnalysisScheduler.Module.ENTITY_OCCLUSION, fastFlight ? 150_000_000L : 50_000_000L,false)) return;
        var config=DynamicChunkie.settings();
        long now=System.nanoTime();
        deadline=WorkCoordinator.deadline(fastFlight ? 300_000L : 900_000L);
        blockReads.clear();
        Vec3 camera = client.gameRenderer.mainCamera().position();
        int budget=config.entityCullingBudget;
        if (config.enableHardwareAdaptive && dev.dynamicchunkie.DynamicChunkie.distanceManager().performance().cpuPressure())
            budget=Math.max(1,budget/2);
        int count = Math.min(budget, scheduled.size());
        for (int i = 0; i < count && System.nanoTime()<deadline; i++) {
            // Reserve every third turn for ordinary candidates even on a dense farm.
            boolean priority=!priorityQueue.isEmpty() && (queue.isEmpty() || ++turn%3!=0);
            Entity entity = (priority?priorityQueue:queue).removeFirst();
            if (!entity.isRemoved() && candidate(entity, client) && inRange(entity, camera.x, camera.y, camera.z)) {
                var frustum=client.gameRenderer.mainCamera().getCullFrustum();
                if (frustum != null && frustum.isVisible(entity.getBoundingBox().inflate(.25))) {
                    sample(entity, camera, now);
                    enqueue(entity,camera.x,camera.y,camera.z);
                    continue;
                }
            }
            scheduled.remove(entity);
            states.remove(entity);
        }
        WorkCoordinator.complete(AnalysisScheduler.Module.ENTITY_OCCLUSION, now, observedSkips);
        observedSkips=0;
    }

    private void enqueue(Entity entity,double x,double y,double z) {
        boolean high=entity instanceof LivingEntity && (entity.distanceToSqr(x,y,z)<32*32 ||
                dev.dynamicchunkie.visual.EntityModelCosts.expensive(entity.getType()));
        (high?priorityQueue:queue).addLast(entity);
    }
    private boolean inRange(Entity entity, double x, double y, double z) {
        double distance = entity.distanceToSqr(x, y, z);
        int maximum = DynamicChunkie.settings().entityCullingDistance;
        return distance >= 64 && distance <= maximum * maximum;
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
        long global=((dev.dynamicchunkie.EnvironmentRevision)world).dynamicchunkie$revision();
        long local=state.globalRevision==global && camera.equals(state.camera) && entity.getBoundingBox().equals(state.box)
                ? state.localRevision : version(camera,entity.getBoundingBox());
        if (camera.equals(state.camera) && entity.getBoundingBox().equals(state.box) && state.localRevision==local &&
                state.blockedSamples!=1) {
            state.checkedAt=now;state.globalRevision=global;return;
        }
        if (state.localRevision!=local) state.blockedSamples=0;
        state.globalRevision=global;state.localRevision=local;
        AABB box = entity.getBoundingBox().inflate(.25);
        boolean blocked = VoxelOcclusion.blocked(camera.x,camera.y,camera.z,
                (box.minX+box.maxX)*.5,(box.minY+box.maxY)*.5,(box.minZ+box.maxZ)*.5,blocks);
        if (blocked) {
            for (int i=0;i<8;i++) {
                int corner=(state.firstCorner+i)&7;
                if (System.nanoTime()>=deadline) {state.checkedAt=0;state.camera=null;return;}
                double x = (corner & 1) == 0 ? box.minX : box.maxX;
                double y = (corner & 2) == 0 ? box.minY : box.maxY;
                double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
                if (!VoxelOcclusion.blocked(camera.x, camera.y, camera.z, x, y, z, blocks)) { state.firstCorner=corner;blocked = false; break; }
            }
        }
        state.blockedSamples = blocked ? Math.min(2, state.blockedSamples + 1) : 0;
        state.camera = camera;
        state.position = position;
        state.box = entity.getBoundingBox();
        state.checkedAt = now;
    }
    private long version(Vec3 camera,AABB bounds) {
        var b=bounds.inflate(.25);
        return ((dev.dynamicchunkie.EnvironmentRevision)world).dynamicchunkie$changes().region(
                (int)Math.floor(Math.min(camera.x,b.minX))>>4,(int)Math.floor(Math.min(camera.y,b.minY))>>4,
                (int)Math.floor(Math.min(camera.z,b.minZ))>>4,(int)Math.floor(Math.max(camera.x,b.maxX))>>4,
                (int)Math.floor(Math.max(camera.y,b.maxY))>>4,(int)Math.floor(Math.max(camera.z,b.maxZ))>>4,false);
    }
    private int blockAt(int x, int y, int z) {
        long key=BlockPos.asLong(x,y,z);
        byte cached=blockReads.get(key);
        if(cached!=0)return cached-2;
        if (!world.getChunkSource().hasChunk(x >> 4, z >> 4)) return -1;
        cursor.set(x, y, z);
        int solid=world.getBlockState(cursor).isSolidRender()?1:0;
        if(blockReads.size()<4096)blockReads.put(key,(byte)(solid+2));
        return solid;
    }
    public boolean shouldCull(Entity entity, double cameraX, double cameraY, double cameraZ) {
        if (!enabled() || world == null || !inRange(entity, cameraX, cameraY, cameraZ) || !candidate(entity, Minecraft.getInstance())) return false;
        // Native shouldRender has admitted this entity. Seed the round-robin queue on demand.
        if (scheduled.size()<Math.min(2048,DynamicChunkie.settings().entityMetadataLimitKiB*4) && scheduled.add(entity))
            enqueue(entity,cameraX,cameraY,cameraZ);
        State state = states.get(entity);
        if (state == null || state.blockedSamples < 2 || System.nanoTime() - state.checkedAt > MAX_AGE) return false;
        if (!state.box.equals(entity.getBoundingBox())) return false;
        if (state.camera==null || state.camera.distanceToSqr(cameraX, cameraY, cameraZ) > 1e-10) return false;
        long global=((dev.dynamicchunkie.EnvironmentRevision)world).dynamicchunkie$revision();
        if(global!=state.globalRevision && version(state.camera,state.box)!=state.localRevision)return false;
        state.globalRevision=global;
        observedSkips++;
        return true;
    }
}
