package dev.dynamicchunkie.loading;

import dev.dynamicchunkie.DynamicChunkie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;

/** Visual/input policy only: native reload objects and completion callbacks remain installed. */
public final class LoadingPresentation {
    private static final ThreadLocal<Integer> INPUT_DEPTH=ThreadLocal.withInitial(() -> 0);
    private LoadingPresentation() {}
    public static boolean simple() {
        var config=DynamicChunkie.settings();
        return config!=null && config.enabled && config.simpleLoadingScreens;
    }
    public static boolean readyForInput() {
        var config=DynamicChunkie.settings();
        if (config==null || !config.enabled || !config.simpleLoadingScreens || !config.loadingInputPassthrough) return false;
        var client=Minecraft.getInstance();
        if (client.level==null || client.player==null) return false;
        var p=client.player.blockPosition();
        return client.level.getChunkSource().hasChunk(p.getX()>>4,p.getZ()>>4);
    }
    public static void beginInput() {INPUT_DEPTH.set(INPUT_DEPTH.get()+1);}
    public static void endInput() {INPUT_DEPTH.set(Math.max(0,INPUT_DEPTH.get()-1));}
    public static Overlay inputOverlay(Overlay actual) {
        return actual instanceof LoadingOverlay && readyForInput()?null:actual;
    }
    public static boolean inInput() {return INPUT_DEPTH.get()>0;}
}
