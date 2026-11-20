package dev.dynamicchunkie;
import dev.dynamicchunkie.visual.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.particles.ParticleTypes;
public final class VisualPoliciesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var backup=context.computeOnClient(client->DynamicChunkie.config().copy());
        try(var world=context.worldBuilder().create()) {
            testParticleMenu(context);
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
                var camera=client.gameRenderer.mainCamera().position();
                for(var entity:client.level.entitiesForRendering())
                    if(entity.getType().getDescriptionId().equals("entity.minecraft.cow") &&
                            EntityVisualPolicy.hide(entity,camera.x,camera.y,camera.z) && entity.getX()<11)
                        throw new AssertionError("Nearest-first limit hid a nearer cow");
                ParticlePolicy.tick(client);var p=client.player.position();int rejected=0;
                for(int i=0;i<10;i++)if(ParticlePolicy.reject(ParticleTypes.SMOKE,p.x,p.y,p.z))rejected++;
                if(rejected<8)throw new AssertionError("Cosmetic spawn budget not enforced");
                if(ParticlePolicy.reject(ParticleTypes.TOTEM_OF_UNDYING,p.x+1000,p.y,p.z))throw new AssertionError("Important particle rejected");
            });
            context.runOnClient(client->{
                var c=DynamicChunkie.config().copy();c.enableEntityTypeLimits=false;c.mobVisualDistance=8;
                c.particleTypeBudgets.put("minecraft:smoke",0);DynamicChunkie.applyConfig(c);
                int hidden=0;var camera=client.gameRenderer.mainCamera().position();
                for(var e:client.level.entitiesForRendering())if(e.getType().getDescriptionId().equals("entity.minecraft.cow"))
                    if(EntityVisualPolicy.hide(e,camera.x,camera.y,camera.z))hidden++;
                if(hidden==0)throw new AssertionError("Independent mob distance not applied");
                if(EntityVisualPolicy.hide(client.player,camera.x,camera.y,camera.z))throw new AssertionError("Local player hidden");
                var pos=client.player.position();ParticlePolicy.tick(client);
                if(!ParticlePolicy.reject(ParticleTypes.SMOKE,pos.x,pos.y,pos.z))throw new AssertionError("Per-type zero budget ignored");
                c.importantParticleTypes.add("minecraft:smoke");DynamicChunkie.applyConfig(c);
                if(ParticlePolicy.reject(ParticleTypes.SMOKE,pos.x,pos.y,pos.z))throw new AssertionError("Custom particle importance ignored");
            });
            world.getServer().runCommand("fill -2 103 -2 3 103 2 minecraft:stone");
            world.getServer().runCommand("fill -2 100 -2 3 102 -2 minecraft:stone");
            world.getServer().runCommand("fill -2 100 2 3 102 2 minecraft:stone");
            world.getServer().runCommand("fill -2 100 -1 -2 102 1 minecraft:stone");
            context.waitFor(client->!client.level.getBlockState(new net.minecraft.core.BlockPos(0,103,0)).isAir() &&
                    !client.level.canSeeSky(client.player.blockPosition()),200);
            context.runOnClient(client->{
                client.player.setDeltaMovement(.3,0,0);
                try {
                    var c=DynamicChunkie.config().copy();c.enableSkyDetection=true;c.predictionLookaheadMs=1000;
                    c.enablePrediction=false;var before=new EnvironmentAnalyzer().analyze(client,c);
                    c.enablePrediction=true;var ahead=new EnvironmentAnalyzer().analyze(client,c);
                    if(ahead.sky() || !ahead.environment().equals("Cave Exit Ahead") || ahead.openness()<=before.openness())
                        throw new AssertionError("Loaded cave exit was not predicted: "+ahead.environment()+" at "+client.player.position()+" sky="+ahead.sky()+" ahead="+ahead.predicted());
                }finally {client.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}
            });
            context.waitTicks(10);
            DynamicChunkie.LOG.info("Visual policy regression passed: cow count limit, cosmetic budget, important effects and profiler rendering");
        }finally {context.runOnClient(client->DynamicChunkie.applyConfig(backup));}
    }
    private void testParticleMenu(ClientGameTestContext context) {
        context.setScreen(()->new ConfigScreen(null));
        for(int i=0;i<13;i++)context.clickScreenButton(">");
        context.clickScreenButton("Particle type budgets");
        String id=context.computeOnClient(client->net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.stream()
                .map(type->net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(type).toString()).sorted().findFirst().orElseThrow());
        context.runOnClient(client->{
            var fields=client.gui.screen().children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox)
                    .map(w->(net.minecraft.client.gui.components.EditBox)w).toList();
            if(fields.isEmpty())throw new AssertionError("Particle budget screen has no fields");
            fields.getFirst().setValue("7");
        });
        context.clickScreenButton("Save");
        context.runOnClient(client->{if(DynamicChunkie.config().particleTypeBudgets.getOrDefault(id,-1)!=7)
            throw new AssertionError("Particle budget menu did not save its draft");});
    }

}
