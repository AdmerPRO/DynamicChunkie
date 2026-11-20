package dev.dynamicchunkie.visual;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.HashMap;
import java.util.List;

/** Contains only scalar inputs captured on the client thread. */
record SelectionSnapshot(Stamp stamp, List<Candidate> candidates) {
    record Stamp(long generation, long geometry, double x, double y, double z) {}
    record Candidate(int id, int type, int limit, double distanceSquared) {}
    SelectionSnapshot { candidates = List.copyOf(candidates); }

    record Result(Stamp stamp, IntOpenHashSet hidden) {
        boolean validFor(Stamp current) { return stamp.equals(current); }
    }

    Result select() {
        var hidden = new IntOpenHashSet();
        var groups = new HashMap<Integer, NearestSelection<Candidate>>();
        for (var item : candidates) {
            hidden.add(item.id());
            groups.computeIfAbsent(item.type(), ignored -> new NearestSelection<>(item.limit()))
                    .offer(item, item.distanceSquared());
        }
        for (var group : groups.values())
            for (int i = 0; i < group.size(); i++) hidden.remove(group.valueAt(i).id());
        return new Result(stamp, hidden);
    }
}
