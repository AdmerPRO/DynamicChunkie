package dev.dynamicchunkie;

/** Requires a materially different target to persist before accepting it. */
final class StableTarget {
    private int accepted, pending = -1;
    private long pendingSince;

    @SuppressWarnings("PMD.UnusedAssignment") // pending is consumed across successive update calls.
    int update(int candidate, long now, RuntimeSettings config) {
        candidate = config.bound(candidate);
        if (accepted < config.minRenderDistance || accepted > config.maxRenderDistance) {
            accepted = candidate;
            pending = -1;
        } else if (Math.abs(candidate - accepted) <= config.deadZoneChunks) {
            pending = -1;
        } else {
            if (pending == -1 || Math.abs(candidate - pending) > config.deadZoneChunks) {
                pending = candidate;
                pendingSince = now;
            }
            if (now - pendingSince >= config.targetStabilityMs * 1_000_000L) {
                accepted = candidate;
                pending = -1;
            }
        }
        return accepted;
    }
    int restrict(int candidate,RuntimeSettings config) {
        int bounded=config.bound(candidate);
        accepted=accepted<config.minRenderDistance?bounded:Math.min(accepted,bounded);
        pending=-1;
        return accepted;
    }
    void reset() { accepted = 0; pending = -1; }
}
