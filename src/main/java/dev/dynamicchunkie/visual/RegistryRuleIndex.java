package dev.dynamicchunkie.visual;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Resolves registry IDs once per type/configuration. Registry access stays lazy on the client thread. */
public final class RegistryRuleIndex<T> {
    public record Rule(int limit, boolean important) {}
    private final Function<T, String> identifier;
    private final IdentityHashMap<T, Rule> resolved = new IdentityHashMap<>();
    private Map<String, Integer> limits = Map.of();
    private Set<String> important = Set.of();

    public RegistryRuleIndex(Function<T, String> identifier) { this.identifier = identifier; }

    public void configure(Map<String, Integer> limits, Set<String> important) {
        this.limits = Map.copyOf(limits);
        this.important = Set.copyOf(important);
        resolved.clear();
    }

    /** A limit of -1 means no per-type override; zero retains the feature's explicit-zero semantics. */
    public Rule rule(T type) {
        Rule cached = resolved.get(type);
        if (cached != null) return cached;
        String id = identifier.apply(type);
        Rule result = new Rule(limits.getOrDefault(id, -1), important.contains(id));
        resolved.put(type, result);
        return result;
    }
}
