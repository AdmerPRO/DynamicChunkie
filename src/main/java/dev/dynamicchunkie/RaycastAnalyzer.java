package dev.dynamicchunkie;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

public final class RaycastAnalyzer {
    private static final Vec3d[] DIRECTIONS = createDirections();
    private static Vec3d[] createDirections() {
        Vec3d[] rays = new Vec3d[26];
        int i = 0;
        for (int y = -1; y <= 1; y++)
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++)
                    if (x != 0 || y != 0 || z != 0) rays[i++] = new Vec3d(x, y, z).normalize();
        return rays;
    }
    public record Sample(double average, double horizontal, double vertical, double clear, double forward) {}
    public Sample sample(ClientWorld world, Entity player) {
        Vec3d start = player.getEyePos();
        double sum = 0, horizontal = 0, vertical = 0, clear = 0;
        for (Vec3d direction : DIRECTIONS) {
            double distance = distance(world, player, start, direction);
            sum += distance;
            if (direction.y == 0) horizontal += distance;
            if (direction.x == 0 && direction.z == 0) vertical += distance;
            if (distance >= 31.9) clear++;
        }
        Vec3d velocity = player.getVelocity();
        double forward = velocity.lengthSquared() > 0.01
                ? distance(world, player, start, velocity.normalize()) / 32 : horizontal / (8 * 32);
        return new Sample(sum / 26, horizontal / (8 * 32), vertical / (2 * 32), clear / 26, forward);
    }
    private double distance(ClientWorld world, Entity player, Vec3d start, Vec3d direction) {
        Vec3d end = start.add(direction.multiply(32));
        // Check every voxel touched (including chunk corners), without requesting a chunk.
        // Unknown terrain is treated as blocked, never as open air.
        if (!BlockViewCheck.loaded(world, start, end)) return 0;
        var hit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? 32 : start.distanceTo(hit.getPos());
    }
    private static final class BlockViewCheck {
        static boolean loaded(ClientWorld world, Vec3d start, Vec3d end) {
            // Conservative bounding rectangle: at most 4x4 chunk lookups for a 32-block ray.
            int minX = MathHelper.floor(Math.min(start.x, end.x)) >> 4;
            int maxX = MathHelper.floor(Math.max(start.x, end.x)) >> 4;
            int minZ = MathHelper.floor(Math.min(start.z, end.z)) >> 4;
            int maxZ = MathHelper.floor(Math.max(start.z, end.z)) >> 4;
            for (int x = minX; x <= maxX; x++)
                for (int z = minZ; z <= maxZ; z++)
                    if (!world.getChunkManager().isChunkLoaded(x, z)) return false;
            return true;
        }
    }
}
