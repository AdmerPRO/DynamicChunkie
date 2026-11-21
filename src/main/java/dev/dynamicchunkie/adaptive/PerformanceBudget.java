package dev.dynamicchunkie.adaptive;

import dev.dynamicchunkie.performance.FrameWindow;

/** Uses actual frame cadence, optional client GPU timing and render-thread CPU time. */
public final class PerformanceBudget {
    private final FrameWindow frames=new FrameWindow(120);
    private int limit=32;
    private double cpuMs=Double.NaN, workMs, gpu=Double.NaN, mean,p95;
    private long started, lastEvaluation, unhealthySince, healthySince,lastSummary,growthBlockedUntil;
    private boolean cpuPressure;
    private String reason="Warming up";

    public void record(double frameMs,double renderMs,double threadCpuMs,double gpuPercent) {
        if (!Double.isFinite(frameMs) || frameMs<=0 || frameMs>1000) return;
        frames.add(frameMs);
        workMs=blend(workMs,renderMs);
        if (Double.isFinite(threadCpuMs) && threadCpuMs>=0) cpuMs=Double.isNaN(cpuMs)?threadCpuMs:blend(cpuMs,threadCpuMs);
        // Zero/negative values mean the game has not supplied usable GPU timing.
        gpu=Double.isFinite(gpuPercent) && gpuPercent>0?Math.min(100,gpuPercent):Double.NaN;
    }
    private double blend(double old,double value) { return old*.9+Math.max(0,value)*.1; }
    public int adjust(int nominal,int current,int minimum,int maximum,int targetFps,long now,
            boolean fpsEnabled,boolean hardwareEnabled) {
        if (started==0) {started=now;limit=maximum;}
        limit=Math.max(minimum,Math.min(maximum,limit));
        if (!fpsEnabled && !hardwareEnabled) {cpuPressure=false;reason="Disabled";return nominal;}
        if (frames.size()<30 || now-started<5_000_000_000L) return Math.min(nominal,limit);
        if (now-lastEvaluation<1_000_000_000L) return Math.min(nominal,limit);
        lastEvaluation=now;
        summarize(now);
        double budget=1000.0/targetFps;
        // One long GC pause can dominate the mean of a small window.
        boolean slow=p95>budget*1.25 || p95>budget && mean>budget*1.10;
        boolean gpuBusy=hardwareEnabled && !Double.isNaN(gpu) && gpu>92;
        cpuPressure=hardwareEnabled && !Double.isNaN(cpuMs) && cpuMs>budget*.8;
        boolean irregular=p95-mean>budget*.25;
        // Don't mistake a stable FPS/VSync cap or a sleeping thread for overload.
        boolean pressure=slow && (gpuBusy || cpuPressure || fpsEnabled && (irregular || (Double.isNaN(cpuMs)?workMs>budget:cpuMs>budget*.8)));
        boolean headroom=mean<budget*.95 && p95<budget*1.1 && !gpuBusy && !cpuPressure;
        if (pressure) {
            growthBlockedUntil=now+5_000_000_000L;
            healthySince=0;if (unhealthySince==0) unhealthySince=now;
            reason=gpuBusy?"GPU timing pressure":cpuPressure?"CPU frame pressure":"Frame-time stalls";
            if (now-unhealthySince>=3_000_000_000L) {
                limit=Math.max(minimum,Math.min(limit,current-2));unhealthySince=now;
            }
        } else {
            unhealthySince=0;
            reason=headroom?"Headroom":"Stable / capped";
            if (headroom) {
                if (healthySince==0) healthySince=now;
                if (now-healthySince>=10_000_000_000L) {limit=Math.min(maximum,limit+1);healthySince=now;}
            } else healthySince=0;
        }
        return Math.max(minimum,Math.min(nominal,limit));
    }
    public void summarize(long now) {
        if(frames.size()==0 || now-lastSummary<1_000_000_000L)return;
        lastSummary=now;
        mean=frames.mean();p95=frames.percentile(.95);
    }
    public void reset() {frames.clear();growthBlockedUntil=0;started=lastEvaluation=unhealthySince=healthySince=0;cpuPressure=false;cpuMs=Double.NaN;mean=p95=workMs=0;gpu=Double.NaN;lastSummary=0;reason="Warming up";}
    public boolean growthBlocked(long now) {return now<growthBlockedUntil;}
    public boolean cpuPressure() {return cpuPressure;}
    public double cpuMs() {return cpuMs;}
    public double meanMs() {return mean;}
    public double p95Ms() {return p95;}
    public double gpuPercent() {return gpu;}
    public String reason() {return reason;}
}
