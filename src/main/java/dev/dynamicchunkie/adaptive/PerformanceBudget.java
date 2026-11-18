package dev.dynamicchunkie.adaptive;

import java.util.Arrays;

/** Uses actual frame cadence, optional client GPU timing and render-thread CPU time. */
public final class PerformanceBudget {
    private final double[] frames=new double[120], scratch=new double[120];
    private int count,index,limit=32;
    private double cpuMs=Double.NaN, workMs, gpu=Double.NaN, mean,p95;
    private long started, lastEvaluation, unhealthySince, healthySince,lastSummary;
    private boolean cpuPressure;
    private String reason="Warming up";

    public void record(double frameMs,double renderMs,double threadCpuMs,double gpuPercent) {
        if (!Double.isFinite(frameMs) || frameMs<=0 || frameMs>1000) return;
        frames[index]=frameMs;index=(index+1)%frames.length;count=Math.min(frames.length,count+1);
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
        if (count<30 || now-started<5_000_000_000L) return Math.min(nominal,limit);
        if (now-lastEvaluation<1_000_000_000L) return Math.min(nominal,limit);
        lastEvaluation=now;
        summarize(now);
        double budget=1000.0/targetFps;
        boolean slow=mean>budget*1.10 || p95>budget*1.25;
        boolean gpuBusy=hardwareEnabled && !Double.isNaN(gpu) && gpu>92;
        cpuPressure=hardwareEnabled && !Double.isNaN(cpuMs) && cpuMs>budget*.8;
        boolean irregular=p95-mean>budget*.25;
        // Don't mistake a stable FPS/VSync cap or a sleeping thread for overload.
        boolean pressure=slow && (gpuBusy || cpuPressure || fpsEnabled && (irregular || (Double.isNaN(cpuMs)?workMs>budget:cpuMs>budget*.8)));
        boolean headroom=mean<budget*.95 && p95<budget*1.1 && !gpuBusy && !cpuPressure;
        if (pressure) {
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
        if(count==0 || now-lastSummary<1_000_000_000L)return;
        lastSummary=now;
        double sum=0;
        for (int i=0;i<count;i++) {sum+=frames[i];scratch[i]=frames[i];}
        mean=sum/count;Arrays.sort(scratch,0,count);
        p95=scratch[Math.min(count-1,(int)Math.ceil(count*.95)-1)];
    }
    public void reset() {count=index=0;started=lastEvaluation=unhealthySince=healthySince=0;cpuPressure=false;cpuMs=Double.NaN;mean=p95=workMs=0;gpu=Double.NaN;lastSummary=0;reason="Warming up";}
    public boolean cpuPressure() {return cpuPressure;}
    public double cpuMs() {return cpuMs;}
    public double meanMs() {return mean;}
    public double p95Ms() {return p95;}
    public double gpuPercent() {return gpu;}
    public String reason() {return reason;}
}
