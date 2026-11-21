package dev.dynamicchunkie.performance;

import java.util.function.LongSupplier;

/** Samples cumulative thread CPU at a fixed cadence and divides by the actual intervening frame count. */
public final class FrameCpuSampler {
    private final LongSupplier readCpu;
    private long lastCpu=-1,lastTime;
    private int frames;
    private boolean initialized;
    public FrameCpuSampler(LongSupplier readCpu) {this.readCpu=readCpu;}
    public double frame(long now,long intervalNs) {
        frames++;
        if(initialized && now-lastTime<intervalNs)return Double.NaN;
        long cpu=readCpu.getAsLong();
        double result=lastCpu>=0 && cpu>=lastCpu && frames>0?(cpu-lastCpu)/1e6/frames:Double.NaN;
        initialized=true;lastCpu=cpu;lastTime=now;frames=0;return result;
    }
    public void reset(){initialized=false;lastCpu=-1;lastTime=0;frames=0;}
}
