package dev.dynamicchunkie.diagnostics;

import jdk.jfr.*;

@Name("dev.dynamicchunkie.ModuleWork")
@Label("DynamicChunkie module work")
@Category("DynamicChunkie")
@StackTrace(false)
public final class ModuleWorkEvent extends Event {
    @Label("Module") public String module;
    @Label("Duration") @Timespan(Timespan.NANOSECONDS) public long durationNanos;
    @Label("Useful decisions") public long benefits;
}
