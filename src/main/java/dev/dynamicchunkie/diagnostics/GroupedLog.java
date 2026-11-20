package dev.dynamicchunkie.diagnostics;

import java.util.HashMap;
import java.util.Map;
import dev.dynamicchunkie.DynamicChunkie;

/** Bounded rate limiting for recurring diagnostic conditions; no per-frame text formatting. */
public final class GroupedLog {
    private static final Map<String,State> states=new HashMap<>();
    private static final class State {long time;int suppressed;}
    private GroupedLog() {}
    public static void error(String key,String message,Throwable failure) {
        if(!states.containsKey(key) && states.size()>=32)return;
        var state=states.computeIfAbsent(key,k->new State());long now=System.nanoTime();
        if(state.time!=0 && now-state.time<30_000_000_000L){state.suppressed++;return;}
        DynamicChunkie.LOG.error("{} ({} repeats suppressed)",message,state.suppressed,failure);state.time=now;state.suppressed=0;
    }
}
