package dev.dynamicchunkie.benchmark;
import dev.dynamicchunkie.sections.HotSoftSections;
import dev.dynamicchunkie.terrain.TerrainCulling;
/** Inert unless an explicit benchmark session is started. No file IO on the client thread. */
public final class FrameCapture {
    private static Session active;
    private FrameCapture() {}
    public static final class Result {
        public int frames,percentileSamples,activeFrames;
        public double wallSeconds,meanMs,p50Ms,p95Ms,p99Ms,fps;
        public double meanCandidates,meanSkipped,meanHotSlots,meanCaptureMicros;
        public long heapStartBytes,heapEndBytes,gcCollections,ownCacheChargeBytes,deferrals,replayed,fallbackFrames;
        public int hotSlots,logicalSoftSlots,pendingRebuilds;
        public boolean samplesCapped;
        public transient double[] intervals;
    }
    private static final class Session {
        final double[] times=new double[65536];
        final long start=System.nanoTime(),heap=usedHeap(),gc=gcCount();
        final long deferred=HotSoftSections.deferredTotal(),replayed=HotSoftSections.replayedTotal(),fallbacks=HotSoftSections.fallbackFrames();
        long last,sumNs,candidates,skipped,hot,captureNs;int frames,samples,activeFrames;
    }
    private static long usedHeap(){var r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    private static long gcCount(){long n=0;for(var gc:java.lang.management.ManagementFactory.getGarbageCollectorMXBeans())if(gc.getCollectionCount()>0)n+=gc.getCollectionCount();return n;}
    public static boolean recording(){return active!=null;}
    public static void begin(){if(active!=null)throw new IllegalStateException("Benchmark already recording");active=new Session();}
    public static void frame() {
        var s=active;if(s==null)return;long now=System.nanoTime();
        if(s.last!=0){long ns=now-s.last;s.sumNs+=ns;s.frames++;
            if(s.samples<s.times.length)s.times[s.samples++]=ns/1e6;
            s.candidates+=TerrainCulling.candidates();s.skipped+=TerrainCulling.skipped();
            if(HotSoftSections.active())s.activeFrames++;
            s.hot+=HotSoftSections.hotCount();s.captureNs+=HotSoftSections.captureNanos();
        }
        s.last=now;
    }
    public static Result end() {
        var s=active;if(s==null)throw new IllegalStateException("No benchmark session");active=null;
        var r=new Result();r.heapEndBytes=usedHeap();r.heapStartBytes=s.heap;r.gcCollections=gcCount()-s.gc;
        r.wallSeconds=(System.nanoTime()-s.start)/1e9;r.frames=s.frames;r.activeFrames=s.activeFrames;r.percentileSamples=s.samples;r.samplesCapped=s.frames>s.samples;
        r.intervals=java.util.Arrays.copyOf(s.times,s.samples);var sorted=r.intervals.clone();java.util.Arrays.sort(sorted);
        if(s.frames>0){r.meanMs=s.sumNs/1e6/s.frames;r.fps=1000/r.meanMs;
            r.meanCandidates=(double)s.candidates/s.frames;r.meanSkipped=(double)s.skipped/s.frames;
            r.meanHotSlots=(double)s.hot/s.frames;r.meanCaptureMicros=(double)s.captureNs/s.frames/1000;}
        if(sorted.length>0){r.p50Ms=quantile(sorted,.5);r.p95Ms=quantile(sorted,.95);r.p99Ms=quantile(sorted,.99);}
        r.hotSlots=HotSoftSections.hotCount();r.logicalSoftSlots=HotSoftSections.logicalSoftCount();
        r.pendingRebuilds=HotSoftSections.pending();r.ownCacheChargeBytes=HotSoftSections.chargedBytes();
        r.deferrals=HotSoftSections.deferredTotal()-s.deferred;r.replayed=HotSoftSections.replayedTotal()-s.replayed;
        r.fallbackFrames=HotSoftSections.fallbackFrames()-s.fallbacks;return r;
    }
    private static double quantile(double[] values,double q){return values[Math.min(values.length-1,(int)Math.ceil(q*values.length)-1)];}
    public static void cancel(){active=null;}
}
