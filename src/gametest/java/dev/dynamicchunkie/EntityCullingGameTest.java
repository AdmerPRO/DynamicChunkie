package dev.dynamicchunkie;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class EntityCullingGameTest implements FabricClientGameTest {
    private Entity pig(Minecraft client) {
        for (Entity entity : client.level.entitiesForRendering())
            if (entity.getType().getDescriptionId().equals("entity.minecraft.pig") && Math.abs(entity.getX() - 10.5) < .05 && Math.abs(entity.getY() - 100) < .1) return entity;
        return null;
    }
    private boolean culled(Minecraft client) {
        Entity entity = pig(client);
        if (entity == null) return false;
        var camera = client.gameRenderer.mainCamera().position();
        return DynamicChunkie.entityCulling().shouldCull(entity, camera.x, camera.y, camera.z);
    }
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> DynamicChunkie.config().copy());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var settings = original.copy();
                settings.enabled = true;
                settings.enableEntityCulling = true;
                DynamicChunkie.applyConfig(settings);
            });
            world.getServer().runCommand("fill -3 99 -3 13 99 3 minecraft:stone");
            world.getServer().runCommand("fill 5 100 -3 5 105 3 minecraft:stone");
            world.getServer().runCommand("tp @a 0.5 100 0.5 -90 0");
            world.getServer().runCommand("summon minecraft:pig 10.5 100 0.5 {NoAI:1b,PersistenceRequired:1b}");
            context.waitFor(this::culled, 400);
            context.runOnClient(client -> {
                var camera = client.gameRenderer.mainCamera().position();
                if (DynamicChunkie.entityCulling().shouldCull(client.player, camera.x, camera.y, camera.z))
                    throw new AssertionError("Local player was culled");
            });
            world.getServer().runCommand("effect give @e[type=minecraft:pig,x=10,y=100,z=0,distance=..3] minecraft:glowing 30 0 true");
            context.waitFor(client -> pig(client) != null && pig(client).isCurrentlyGlowing(), 200);
            context.runOnClient(client -> { if (culled(client)) throw new AssertionError("Glowing entity was culled"); });
            world.getServer().runCommand("effect clear @e[type=minecraft:pig,x=10,y=100,z=0,distance=..3]");
            context.waitFor(client -> pig(client) != null && !pig(client).isCurrentlyGlowing(), 200);
            world.getServer().runCommand("fill 5 100 -3 5 105 3 minecraft:glass");
            context.waitFor(client -> pig(client) != null && !culled(client), 400);
            world.getServer().runCommand("fill 5 100 -3 5 105 3 minecraft:stone");
            context.waitFor(this::culled, 400);
            context.runOnClient(client -> {
                var settings = DynamicChunkie.config().copy();
                settings.enableEntityCulling = false;
                DynamicChunkie.applyConfig(settings);
                if (culled(client)) throw new AssertionError("Disable did not reveal entity");
                DynamicChunkie.LOG.info("Entity culling regression passed: stone hides, glass/glowing/player remain visible, disabling reveals immediately");
            });
        } finally {
            context.runOnClient(client -> DynamicChunkie.applyConfig(original));
        }
    }
}
