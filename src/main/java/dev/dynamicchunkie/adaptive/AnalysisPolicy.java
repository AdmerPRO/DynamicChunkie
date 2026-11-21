package dev.dynamicchunkie.adaptive;

public final class AnalysisPolicy {
    private AnalysisPolicy() {}
    public static boolean approachingWall(boolean flight,double speed,double forward) {
        // Zero is also the sentinel for unloaded terrain; never treat it as a proven wall.
        return flight && speed>8 && forward>0 && forward<.4;
    }
    public static int interval(double speed,boolean elytra,boolean lookingAround,boolean cpuPressure,
            int stationary,int walking,int flying,boolean adaptive,int fixed) {
        int base=adaptive?(elytra?flying:speed>.15||lookingAround?walking:stationary):fixed;
        return Math.min(10000,cpuPressure?base*2:base);
    }
    public static double directional(double horizontal,double front,double gaze) {
        return Math.max(0,Math.min(1,horizontal+.35*(Math.max(front,gaze)-horizontal)));
    }
    public static double predictionBoost(double speed,double horizontal,double movement,double gaze,double ahead,double alignment) {
        if (speed<8 || horizontal<.55 || movement<.65 || gaze<.3 || ahead<.6) return 0;
        return .12*Math.min(1,speed/35)*horizontal*movement*ahead*(.75+.25*Math.max(0,alignment));
    }
}
