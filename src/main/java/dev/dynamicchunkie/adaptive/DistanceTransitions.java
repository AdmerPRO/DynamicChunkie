package dev.dynamicchunkie.adaptive;

/** Movement detection and bounded increase rates. No world or option mutation. */
public final class DistanceTransitions {
    private Object world;
    private double x,y,z;
    private long lastCombat=Long.MIN_VALUE, lastChange;
    private boolean recovering, combatRestore;

    public void combat(long now) {
        lastCombat=now;
        combatRestore |= recovering;
        recovering=false;
    }
    public boolean observe(Object level, double px,double py,double pz,double speedPerTick,
            boolean hurt,long now,boolean enabled,int threshold,int combatGraceMs) {
        if (hurt) combat(now);
        boolean fighting=lastCombat!=Long.MIN_VALUE && now-lastCombat<=combatGraceMs*1_000_000L;
        double dx=px-x,dy=py-y,dz=pz-z;
        double expected=Math.max(threshold,speedPerTick*4+8);
        boolean teleported=world!=level || dx*dx+dy*dy+dz*dz>expected*expected;
        world=level;x=px;y=py;z=pz;
        if (teleported && enabled && !fighting) {
            recovering=true;combatRestore=false;lastChange=now;return true;
        }
        if (fighting || !enabled) recovering=false;
        return false;
    }
    public int next(int current,int target,long now,int normalIntervalMs) {
        if (target<=current) {
            if (target<current) lastChange=now;
            combatRestore=false;
            return target;
        }
        if (combatRestore) {
            combatRestore=false;lastChange=now;return target;
        }
        long interval=(recovering?1000L:normalIntervalMs)*1_000_000;
        if (now-lastChange<interval) return current;
        // No accumulated burst after a paused/slow frame.
        int step=recovering?1:Math.max(1,(int)Math.floor(2*normalIntervalMs/1000.0));
        int result=Math.min(target,current+step);
        lastChange=now;
        if (result==target) recovering=false;
        return result;
    }
    public void pause(long now) { lastChange=now; }
    public void reset() { world=null;recovering=false;combatRestore=false;lastChange=0;lastCombat=Long.MIN_VALUE; }
    public boolean recovering() { return recovering; }
}
