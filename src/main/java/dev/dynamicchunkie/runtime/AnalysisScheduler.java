package dev.dynamicchunkie.runtime;

/** Shared cooperative tick budget, staggered deadlines and bounded evidence-based cooldowns. */
public final class AnalysisScheduler {
    public enum Module { ENVIRONMENT, ENTITY_OCCLUSION, TERRAIN_DISCOVERY, ENTITY_SELECTION, CACHE_MAINTENANCE }
    public record Sample(Module module, long calls, long totalNs, double meanMicros, long benefits, int cooldownMultiplier) {}
    private final NanoClock clock;
    private final WorkBudget budget;
    private final long[] due = new long[Module.values().length], calls = due.clone(), benefits = due.clone(), totalNs = due.clone();
    private final double[] cost = new double[due.length];
    private final int[] empty = new int[due.length];
    private long epoch = Long.MIN_VALUE;
    private boolean adaptive;

    public AnalysisScheduler(NanoClock clock) { this.clock = clock; budget = new WorkBudget(clock); }
    public void beginTick(long budgetNs, boolean adapt) {
        if (epoch == Long.MIN_VALUE) epoch = clock.now();
        adaptive = adapt; budget.begin(budgetNs);
    }
    public boolean admit(Module module, long intervalNs, boolean urgent) {
        int i = module.ordinal(); long now = clock.now();
        if (!budget.available()) return false;
        if (!urgent && now < (due[i] == 0 ? epoch + i * 20_000_000L : due[i])) return false;
        due[i] = now + intervalNs * multiplier(module);
        return true;
    }
    public void complete(Module module, long started, long useful) {
        int i = module.ordinal(); long elapsed = Math.max(0, clock.now() - started);
        cost[i] = calls[i] == 0 ? elapsed : cost[i] * .9 + elapsed * .1;
        totalNs[i] += elapsed; calls[i]++; benefits[i] += Math.max(0, useful);
        empty[i] = useful > 0 ? 0 : Math.min(1000, empty[i] + 1);
    }
    private int multiplier(Module module) {
        // Environment and maintenance are correctness-critical; never suppress them by render profitability.
        if (!adaptive || module == Module.ENVIRONMENT || module == Module.CACHE_MAINTENANCE) return 1;
        int i = module.ordinal();
        return empty[i] >= 20 && cost[i] > 100_000 ? 4 : empty[i] >= 10 && cost[i] > 100_000 ? 2 : 1;
    }
    public boolean available() { return budget.available(); }
    public long remaining() { return budget.remaining(); }
    public java.util.List<Sample> snapshot() {
        var result = new java.util.ArrayList<Sample>();
        for (var module : Module.values()) { int i=module.ordinal(); result.add(new Sample(module,calls[i],totalNs[i],cost[i]/1000,benefits[i],multiplier(module))); }
        return java.util.List.copyOf(result);
    }
    public void reset() {
        java.util.Arrays.fill(due,0);java.util.Arrays.fill(calls,0);java.util.Arrays.fill(benefits,0);
        java.util.Arrays.fill(totalNs,0);java.util.Arrays.fill(cost,0);java.util.Arrays.fill(empty,0);epoch=Long.MIN_VALUE;budget.reset();
    }
}
