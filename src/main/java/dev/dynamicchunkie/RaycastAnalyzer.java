package dev.dynamicchunkie;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
    private boolean previewSky;
    public boolean previewSky() {return previewSky;}
    public record Sample(double average, double horizontal, double vertical, double clear, double forward,
            double front,double back,double left,double right,double gaze,double up,double down) {}
    public Sample sample(ClientLevel world, Entity player) {
        Vec3 start = player.getEyePosition();
        ChunkRayTraversal.LoadedChunks loaded = world.getChunkSource()::hasChunk;
        double sum = 0, horizontal = 0, vertical = 0, clear = 0, up=0, down=0;
        Vec3 look = player.getLookAngle();
        double yaw = Math.toRadians(player.getYRot());
        double facingX = -Math.sin(yaw), facingZ = Math.cos(yaw);
        double front=0,back=0,left=0,right=0,weightFront=0,weightBack=0,weightLeft=0,weightRight=0;
        for (Vec3 direction : DIRECTIONS) {
            double distance = distance(world, player, start, direction, loaded);
            sum += distance;
            if (direction.y == 0) {
                horizontal += distance;
                double ahead=direction.x*facingX+direction.z*facingZ;
                double side=direction.x*(-facingZ)+direction.z*facingX;
                if (ahead>0) {front+=distance*ahead;weightFront+=ahead;}
                if (ahead<0) {back-=distance*ahead;weightBack-=ahead;}
                if (side>0) {right+=distance*side;weightRight+=side;}
                if (side<0) {left-=distance*side;weightLeft-=side;}
            }
            if (direction.x == 0 && direction.z == 0) {
                vertical += distance;
                if(direction.y>0)up=distance/32;else down=distance/32;
            }
            if (distance >= 31.9) clear++;
        }
        Vec3 velocity = player.getDeltaMovement();
        double forward = velocity.lengthSqr() > 0.01
                ? distance(world, player, start, velocity.normalize(), loaded) / 32 : horizontal / (8 * 32);
        return new Sample(sum / 26, horizontal / (8 * 32), vertical / (2 * 32), clear / 26, forward,
                front/(weightFront*32),back/(weightBack*32),left/(weightLeft*32),right/(weightRight*32),
                distance(world,player,start,look,loaded)/32,up,down);
    }
    public double preview(ClientLevel world, Entity player, int horizonMs) {
        Vec3 velocity=player.getDeltaMovement(),look=player.getLookAngle();
        double speed=velocity.length();
        previewSky=false;
        if (speed<.02 || horizonMs==0) return 0;
        Vec3 direction=velocity.normalize().scale(.8).add(look.scale(.2)).normalize();
        double blocks=Math.min(24,speed*horizonMs/50.0);
        Vec3 start=player.getEyePosition(),future=start.add(direction.scale(blocks));
        ChunkRayTraversal.LoadedChunks loaded=world.getChunkSource()::hasChunk;
        if (!ChunkRayTraversal.allLoaded(start.x,start.z,future.x,future.z,loaded)) return 0;
        var path=world.clip(new ClipContext(start,future,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if (path.getType()!=HitResult.Type.MISS) return 0;
        previewSky=world.canSeeSky(net.minecraft.core.BlockPos.containing(future));
        return (.5*distance(world,player,future,direction,loaded)+.35*distance(world,player,future,look,loaded)+
                .15*distance(world,player,future,new Vec3(0,1,0),loaded))/32;
    }
    private double distance(ClientLevel world, Entity player, Vec3 start, Vec3 direction,
            ChunkRayTraversal.LoadedChunks loaded) {
        Vec3 end = start.add(direction.scale(32));
        // Check only chunks traversed by this ray; querying availability never requests chunks.
        // Unknown terrain is treated as blocked, never as open air.
        if (!ChunkRayTraversal.allLoaded(start.x, start.z, end.x, end.z, loaded)) return 0;
        var hit = world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? 32 : start.distanceTo(hit.getLocation());
    }
}
