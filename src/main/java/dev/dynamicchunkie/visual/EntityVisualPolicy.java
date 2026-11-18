package dev.dynamicchunkie.visual;

import dev.dynamicchunkie.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/** Client-only selection. Never removes entities or changes their ticking/network state. */
public final class EntityVisualPolicy {
    private static final Set<Integer> tracked=new HashSet<>(), selected=new HashSet<>();
    private static final List<Entity> candidates=new ArrayList<>();
    private static Object world;
    private static long sampled;
    private static double scale=1;
    private EntityVisualPolicy() {}
    public static void tick(Minecraft client) {
        var c=DynamicChunkie.configOrNull();
        if(c==null || !c.enabled || client.level==null || client.player==null) {
            tracked.clear();selected.clear();candidates.clear();world=null;scale=1;return;
        }
        double mean=DynamicChunkie.distanceManager().performance().meanMs();
        double desired=VisualBudget.scale(mean,c.targetFps,client.options.framerateLimit().get());
        scale+=(desired-scale)*.025;
        long now=System.nanoTime();
        if(world==client.level && now-sampled<250_000_000L)return;
        world=client.level;sampled=now;tracked.clear();selected.clear();candidates.clear();
        if(!c.enableEntityTypeLimits)return;
        for(Entity entity:client.level.entitiesForRendering()) {
            if(entity instanceof Player || c.protectImportantEntities && (entity.isCurrentlyGlowing() || entity.hasCustomName()))continue;
            String id=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if(c.entityTypeLimits.getOrDefault(id,0)>0)candidates.add(entity);
        }
        var camera=client.gameRenderer.mainCamera().position();
        candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(camera)));
        Map<String,Integer> counts=new HashMap<>();
        for(Entity entity:candidates) {
            String key=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            int limit=c.entityTypeLimits.getOrDefault(key,0);
            tracked.add(entity.getId());
            int count=counts.merge(key,1,Integer::sum);
            if(limit<=0 || count<=limit)selected.add(entity.getId());
        }
        candidates.clear();
    }
    public static boolean hide(Entity entity,double x,double y,double z) {
        var c=DynamicChunkie.configOrNull();
        if(c==null || !c.enabled)return false;
        if(entity==Minecraft.getInstance().player || c.protectImportantEntities && (entity.isCurrentlyGlowing() || entity.hasCustomName()))return false;
        boolean hidden=c.enableEntityTypeLimits && tracked.contains(entity.getId()) && !selected.contains(entity.getId());
        if(c.enableAdaptiveEntityDistance) {
            double distance=entity instanceof Player?c.playerVisualDistance:
                    entity instanceof ItemEntity?c.itemVisualDistance:
                    entity instanceof ArmorStand?c.armorStandVisualDistance:c.mobVisualDistance;
            if(!(entity instanceof Player))distance*=scale;
            hidden|=entity.distanceToSqr(x,y,z)>distance*distance;
        }
        return hidden;
    }
    public static double scale() {return scale;}
    public static double blockDistance() {return DynamicChunkie.config().blockEntityVisualDistance*scale;}
}
