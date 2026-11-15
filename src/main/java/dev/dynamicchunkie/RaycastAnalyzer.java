package dev.dynamicchunkie;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;

public final class RaycastAnalyzer {
    private static final Vec3[] DIRECTIONS = createDirections();
    private static Vec3[] createDirections() {
        Vec3[] rays = new Vec3[26];
        int i = 0;
        for (int y = -1; y <= 1; y++)
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++)
                    if (x != 0 || y != 0 || z != 0) rays[i++] = new Vec3(x, y, z).normalize();
        return rays;
    }
    public record Sample(double average, double horizontal, double vertical, double clear, double forward) {}
    public Sample sample(ClientLevel world, Entity player) {
        Vec3 start = player.getEyePosition();
        double sum = 0, horizontal = 0, vertical = 0, clear = 0;
        for (Vec3 direction : DIRECTIONS) {
            double distance = distance(world, player, start, direction);
            sum += distance;
            if (direction.y == 0) horizontal += distance;
            if (direction.x == 0 && direction.z == 0) vertical += distance;
            if (distance >= 31.9) clear++;
        }
        Vec3 velocity = player.getDeltaMovement();
        double forward = velocity.lengthSqr() > 0.01
                ? distance(world, player, start, velocity.normalize()) / 32 : horizontal / (8 * 32);
        return new Sample(sum / 26, horizontal / (8 * 32), vertical / (2 * 32), clear / 26, forward);
    }
    private double distance(ClientLevel world, Entity player, Vec3 start, Vec3 direction) {
        Vec3 end = start.add(direction.scale(32));
        // Check every voxel touched (including chunk corners), without requesting a chunk.
        // Unknown terrain is treated as blocked, never as open air.
        if (!BlockViewCheck.loaded(world, start, end)) return 0;
        var hit = world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? 32 : start.distanceTo(hit.getLocation());
    }
    private static final class BlockViewCheck {
        static boolean loaded(ClientLevel world, Vec3 start, Vec3 end) {
            // Conservative bounding rectangle: at most 4x4 chunk lookups for a 32-block ray.
            int minX = Mth.floor(Math.min(start.x, end.x)) >> 4;
            int maxX = Mth.floor(Math.max(start.x, end.x)) >> 4;
            int minZ = Mth.floor(Math.min(start.z, end.z)) >> 4;
            int maxZ = Mth.floor(Math.max(start.z, end.z)) >> 4;
            for (int x = minX; x <= maxX; x++)
                for (int z = minZ; z <= maxZ; z++)
                    if (!world.getChunkSource().hasChunk(x, z)) return false;
            return true;
        }
    }
}
