package dev.dynamicchunkie.visual;

import net.minecraft.client.CloudStatus;

/** Downgrades after one sustained second; recovery requires five stable seconds. */
public final class CloudGovernor {
    private int tier = 2, pending = 2;
    private long since;

    public void reset() { tier = pending = 2; since = 0; }
    public void update(double scale, long now) {
        int target = switch (tier) {
            case 0 -> scale > .56 ? 1 : 0;
            case 1 -> scale < .48 ? 0 : scale > .84 ? 2 : 1;
            default -> scale < .76 ? 1 : 2;
        };
        if (target != pending) { pending = target; since = now; }
        if (tier != pending && now-since >= (pending<tier?1_000_000_000L:5_000_000_000L)) {
            tier = pending;
            since = now;
        }
    }
    public CloudStatus limit(CloudStatus selected) {
        return VisualBudget.clouds(selected, tier==2?1:tier==1?.65:0);
    }
}
