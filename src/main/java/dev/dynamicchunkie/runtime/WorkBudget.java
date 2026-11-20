package dev.dynamicchunkie.runtime;

/** Cooperative deadline; native operations already in progress are never interrupted. */
public final class WorkBudget {
    private final NanoClock clock;
    private long deadline = Long.MAX_VALUE;
    public WorkBudget(NanoClock clock) { this.clock = clock; }
    public void begin(long nanoseconds) { deadline = clock.now() + Math.max(0, nanoseconds); }
    public boolean available() { return clock.now() < deadline; }
    public long remaining() { return Math.max(0, deadline - clock.now()); }
    public void reset() { deadline = Long.MAX_VALUE; }
}
