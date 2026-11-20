package dev.dynamicchunkie.runtime;

import dev.dynamicchunkie.DynamicChunkie;
import dev.dynamicchunkie.diagnostics.ModuleWorkEvent;
import jdk.jfr.EventType;

/** All world-facing work remains on the client thread. One deadline is shared across module calls. */
public final class WorkCoordinator {
    private static final AnalysisScheduler scheduler = new AnalysisScheduler(NanoClock.SYSTEM);
    private static final long[] lastEvent = new long[AnalysisScheduler.Module.values().length];
    private static boolean inTick;
    private WorkCoordinator() {}
    public static void beginTick() {
        var c=DynamicChunkie.settings();
        scheduler.beginTick(c.sharedAnalysisBudgetMicros*1000L,c.enableAdaptiveModuleBudgets);inTick=true;
    }
    public static void endTick() { inTick=false; }
    public static boolean available() { return !inTick || scheduler.available(); }
    public static long deadline(long maximumNs) { return System.nanoTime()+(!inTick?maximumNs:Math.min(maximumNs,scheduler.remaining())); }
    public static boolean admit(AnalysisScheduler.Module module,long intervalNs,boolean urgent) {
        return !inTick || scheduler.admit(module,intervalNs,urgent);
    }
    public static void complete(AnalysisScheduler.Module module,long start,long benefits) {
        scheduler.complete(module,start,benefits);
        var c=DynamicChunkie.settings();long now=System.nanoTime();int i=module.ordinal();
        if(c.enableJfrEvents && now-lastEvent[i]>=1_000_000_000L && EventType.getEventType(ModuleWorkEvent.class).isEnabled()) {
            var event=new ModuleWorkEvent();event.module=module.name();event.durationNanos=Math.max(0,now-start);event.benefits=benefits;event.commit();lastEvent[i]=now;
        }
    }
    public static java.util.List<AnalysisScheduler.Sample> snapshot() {return scheduler.snapshot();}
    public static void reset() {scheduler.reset();inTick=false;java.util.Arrays.fill(lastEvent,0);}
}
