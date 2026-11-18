package dev.dynamicchunkie.visual;
import net.minecraft.client.CloudStatus;
/** Shared bounded quality policy; a user FPS cap counts as the target, not as overload. */
public final class VisualBudget {
    private VisualBudget() {}
    public static double scale(double meanMs,int targetFps,int frameLimit) {
        int fps=Math.max(15,Math.min(targetFps,frameLimit));
        return !Double.isFinite(meanMs) || meanMs<=0?1:Math.max(.35,Math.min(1,1000.0/fps/meanMs));
    }
    public static CloudStatus clouds(CloudStatus selected,double scale) {
        if(selected==CloudStatus.OFF || scale<.5)return CloudStatus.OFF;
        return scale<.8 && selected==CloudStatus.FANCY?CloudStatus.FAST:selected;
    }
}
