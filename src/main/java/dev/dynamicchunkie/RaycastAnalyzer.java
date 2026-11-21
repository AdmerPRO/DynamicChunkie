package dev.dynamicchunkie;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

public final class RaycastAnalyzer {
    private static final Vec3 UP=new Vec3(0,1,0);
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
    public record Preview(double openness,boolean sky) {}
    private static final Preview NO_PREVIEW=new Preview(0,false);
    public record Sample(double average, double horizontal, double vertical, double clear, double forward,
            double front,double back,double left,double right,double gaze,double up,double down) {}
    private final double[] distances = new double[DIRECTIONS.length];
    private final long[] columnKeys=new long[64], columnVersions=new long[64];
    private final byte[] availability=new byte[64];
    private ClientLevel availabilityWorld;
    private final ChunkRayTraversal.LoadedChunks loaded=this::loaded;
    private boolean loaded(int x,int z) {
        long key=(long)x<<32 | Integer.toUnsignedLong(z);
        int slot=(int)it.unimi.dsi.fastutil.HashCommon.mix(key)&63;
        long stamp=((EnvironmentRevision)availabilityWorld).dynamicchunkie$changes().columnVersion(x,z);
        if (availability[slot]==0 || columnKeys[slot]!=key || columnVersions[slot]!=stamp) {
            columnKeys[slot]=key;
            columnVersions[slot]=stamp;
            availability[slot]=(byte)(availabilityWorld.getChunkSource().hasChunk(x,z)?2:1);
        }
        return availability[slot]==2;
    }
    private void useWorld(ClientLevel world) {
        if (availabilityWorld!=world) {
            java.util.Arrays.fill(availability,(byte)0);
            availabilityWorld=world;
        }
    }
    private int nextRay;
    private double gazeSample;
    private final int[] rayOrder=new int[DIRECTIONS.length];
    private void orderRays(Vec3 forward) {
        // Horizontal sight lines toward the opening and the sky ray resolve ambiguous passages first.
        for(int i=0;i<rayOrder.length;i++) {
            int j=i;
            double rank=priority(DIRECTIONS[i],forward);
            while(j>0 && priority(DIRECTIONS[rayOrder[j-1]],forward)<rank) {
                rayOrder[j]=rayOrder[j-1];
                j--;
            }
            rayOrder[j]=i;
        }
    }
    private static double priority(Vec3 direction,Vec3 forward) {
        return direction.dot(forward)+(direction.y==0?.7:direction.y==1?.5:0);
    }
    private Vec3 origin, look, velocity;
    private double yaw;
    private long version;
    private ClientLevel level;
    public void reset() {nextRay=0;origin=null;level=null;useWorld(null);}

    /** A native clip cannot be interrupted. Check the shared deadline before starting each clip. */
    public Sample sample(ClientLevel world, Entity player, long revision, long deadline) {
        Vec3 eye = player.getEyePosition();
        if (origin == null || level != world || revision != version || eye.distanceToSqr(origin)>64 ||
                player.getLookAngle().dot(look)<.94 || player.getDeltaMovement().lengthSqr()<.00006 && !eye.equals(origin)) {
            nextRay=0;
            level=world;
            origin=eye;
            velocity=player.getDeltaMovement();
            look=player.getLookAngle();
            yaw=Math.toRadians(player.getYRot());
            version=revision;
            orderRays(look);
        }
        useWorld(world);
        while (nextRay<DIRECTIONS.length) {
            if (System.nanoTime()>=deadline) return null;
            int index=rayOrder[nextRay];
            distances[index]=distance(world,player,origin,DIRECTIONS[index],loaded);
            nextRay++;
        }
        if (System.nanoTime()>=deadline) return null;
        if (nextRay==DIRECTIONS.length) {
            gazeSample=distance(world,player,origin,look,loaded)/32;
            nextRay++;
        }
        if (System.nanoTime()>=deadline) return null;
        double gaze=gazeSample;
        Vec3 motion=velocity.normalize();
        double forward=velocity.lengthSqr() <= .01 ? -1 :
                motion.distanceToSqr(look)<1e-12 ? gaze : distance(world,player,origin,motion,loaded)/32;
        origin=null;nextRay=0;level=null;
        double sum=0,horizontal=0,vertical=0,clear=0,up=0,down=0;
        double facingX = -Math.sin(yaw), facingZ = Math.cos(yaw);
        double front=0,back=0,left=0,right=0,weightFront=0,weightBack=0,weightLeft=0,weightRight=0;
        for (int i=0;i<DIRECTIONS.length;i++) {
            Vec3 direction=DIRECTIONS[i];
            double distance=distances[i];
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
        if (forward<0) forward=horizontal/(8*32);
        return new Sample(sum / 26, horizontal / (8 * 32), vertical / (2 * 32), clear / 26, forward,
                front/(weightFront*32),back/(weightBack*32),left/(weightLeft*32),right/(weightRight*32),
                gaze,up,down);
    }
    /** Recheck the opening a player is looking into between full geometry samples. */
    public Sample refreshOpening(ClientLevel world,Entity player,Sample previous,long deadline) {
        if (previous.up()>.7 || previous.horizontal()>.65 ||
                Math.max(previous.gaze(),previous.front())-previous.horizontal()<.15 ||
                System.nanoTime()>=deadline) return previous;
        useWorld(world);
        double gaze=distance(world,player,player.getEyePosition(),player.getLookAngle(),loaded)/32;
        if (gaze==previous.gaze()) return previous;
        return new Sample(previous.average(),previous.horizontal(),previous.vertical(),previous.clear(),previous.forward(),
                previous.front(),previous.back(),previous.left(),previous.right(),gaze,previous.up(),previous.down());
    }
    public Preview preview(ClientLevel world, Entity player, int horizonMs,long deadline) {
        Vec3 velocity=player.getDeltaMovement(),look=player.getLookAngle();
        double speed=velocity.length();
        if (speed<.02 || horizonMs==0 || System.nanoTime()>=deadline) return NO_PREVIEW;
        Vec3 direction=velocity.normalize().scale(.8).add(look.scale(.2)).normalize();
        double blocks=Math.min(24,speed*horizonMs/50.0);
        Vec3 start=player.getEyePosition(),future=start.add(direction.scale(blocks));
        useWorld(world);
        if (!ChunkRayTraversal.allLoaded(start.x,start.z,future.x,future.z,loaded)) return NO_PREVIEW;
        var path=world.clip(new ClipContext(start,future,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if (path.getType()!=HitResult.Type.MISS) return NO_PREVIEW;
        boolean sky=world.canSeeSky(net.minecraft.core.BlockPos.containing(future));
        if (System.nanoTime()>=deadline) return NO_PREVIEW;
        double motion=distance(world,player,future,direction,loaded);
        if (System.nanoTime()>=deadline) return NO_PREVIEW;
        double gaze=direction.distanceToSqr(look)<1e-12 ? motion : distance(world,player,future,look,loaded);
        if (System.nanoTime()>=deadline) return NO_PREVIEW;
        return new Preview((.5*motion+.35*gaze+
                .15*distance(world,player,future,UP,loaded))/32,sky);
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
