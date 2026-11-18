package dev.dynamicchunkie.visual;
import dev.dynamicchunkie.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
/** Main-thread frame history; counts refer to extracted render states, not simulated entities. */
public final class VisualProfiler {
    private static final double[] frames=new double[180];
    private static int index, count, entities, blocks;
    private static long lastFrame;
    private VisualProfiler() {}
    public static void frame() {
        var client=Minecraft.getInstance();long now=System.nanoTime();
        if(client.level!=null && !client.isPaused() && lastFrame!=0) {
            frames[index]=(now-lastFrame)/1e6;index=(index+1)%frames.length;count=Math.min(count+1,frames.length);
        } else count=0;
        lastFrame=now;
    }
    public static void extracted(int entityCount,int blockCount) {entities=entityCount;blocks=blockCount;}
    public static void draw(GuiGraphicsExtractor graphics) {
        var client=Minecraft.getInstance();var c=DynamicChunkie.configOrNull();
        if(c==null || !c.profilerOverlay || client.level==null || client.gui.hud.isHidden())return;
        var manager=DynamicChunkie.distanceManager();var perf=manager.performance();
        int visible=Math.max(0,dev.dynamicchunkie.terrain.TerrainCulling.candidates()-dev.dynamicchunkie.terrain.TerrainCulling.skipped());
        String renderer=RenderCache.sodiumPresent()?"Sodium":"vanilla";
        String[] lines={"DynamicChunkie profiler / "+renderer,
            String.format(java.util.Locale.ROOT,"FPS %d | frame mean/p95 %.1f / %.1f ms",client.getFps(),perf.meanMs(),perf.p95Ms()),
            String.format(java.util.Locale.ROOT,"Render-thread CPU EMA %.1f ms",perf.cpuMs()),
            "Terrain visible candidates: "+visible+" | extra skipped: "+dev.dynamicchunkie.terrain.TerrainCulling.skipped(),
            "Terrain occluders: "+dev.dynamicchunkie.terrain.TerrainCulling.occluders()+" | cache charged: "+dev.dynamicchunkie.terrain.TerrainCulling.chargedBytes()+" B",
            "Hot sections: "+dev.dynamicchunkie.sections.HotSoftSections.hotCount()+" | deferred: "+dev.dynamicchunkie.sections.HotSoftSections.pending()+" | scan us: "+dev.dynamicchunkie.sections.HotSoftSections.captureNanos()/1000,
            "Extracted entities / block entities: "+entities+" / "+blocks,
            "GPU timing: "+(Double.isNaN(perf.gpuPercent())?"unavailable":String.format(java.util.Locale.ROOT,"%.0f%%",perf.gpuPercent())),
            "Analysis cache charged: "+manager.cacheBytes()+" bytes (geometry VRAM unknown)",
            "Frame time history: 180 frames / 0-100 ms"};
        int y=graphics.guiHeight()-176;
        for(String line:lines) {
            graphics.fill(5,y-1,12+client.font.width(line),y+10,0xB0000000);
            graphics.text(client.font,line,8,y,0xFFFFFFFF,true);y+=12;
        }
        graphics.fill(5,y,185,y+50,0xB0000000);
        for(int i=0;i<count;i++) {
            double ms=frames[(index-count+i+frames.length)%frames.length];
            int height=(int)Math.min(48,ms*.48);
            graphics.fill(5+i,y+49-height,6+i,y+49,ms>33?0xFFFF5555:0xFF55FF88);
        }
    }
}
