package dev.dynamicchunkie;
import dev.dynamicchunkie.visual.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.particles.ParticleTypes;
public final class VisualPoliciesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var backup=context.computeOnClient(client->DynamicChunkie.config().copy());
        try(var world=context.worldBuilder().create()) {
            world.getServer().runCommand("tp @a 0.5 100 0.5 -90 0");
            world.getServer().runCommand("fill -3 99 -3 16 99 3 minecraft:stone");
            for(int x:new int[]{8,10,12})world.getServer().runCommand("summon minecraft:cow "+x+" 100 0 {NoAI:1b}");
            context.runOnClient(client->{
                var c=backup.copy();c.enabled=true;c.enableEntityTypeLimits=true;
                c.entityTypeLimits.put("minecraft:cow",2);c.enableAdaptiveEntityDistance=true;
                c.mobVisualDistance=128;c.enableAdaptiveParticles=true;c.particleSpawnBudget=2;
                c.profilerOverlay=true;c.enableSmartClouds=true;
                if(!DynamicChunkie.applyConfig(c))throw new AssertionError("Cannot save visual settings");
            });
            context.waitFor(client->{
                int found=0,hidden=0;var camera=client.gameRenderer.mainCamera().position();
                for(var e:client.level.entitiesForRendering())if(e.getType().getDescriptionId().equals("entity.minecraft.cow")) {
                    found++;if(EntityVisualPolicy.hide(e,camera.x,camera.y,camera.z))hidden++;
                }
                return found==3 && hidden==1;
            },200);
            context.runOnClient(client->{
                ParticlePolicy.tick(client);var p=client.player.position();int rejected=0;
                for(int i=0;i<10;i++)if(ParticlePolicy.reject(ParticleTypes.SMOKE,p.x,p.y,p.z))rejected++;
                if(rejected<8)throw new AssertionError("Cosmetic spawn budget not enforced");
                if(ParticlePolicy.reject(ParticleTypes.TOTEM_OF_UNDYING,p.x+1000,p.y,p.z))throw new AssertionError("Important particle rejected");
            });
            context.waitTicks(10);
            DynamicChunkie.LOG.info("Visual policy regression passed: cow count limit, cosmetic budget, important effects and profiler rendering");
        }finally {context.runOnClient(client->DynamicChunkie.applyConfig(backup));}
    }
}
