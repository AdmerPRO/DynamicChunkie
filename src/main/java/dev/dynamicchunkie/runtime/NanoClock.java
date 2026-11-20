package dev.dynamicchunkie.runtime;

@FunctionalInterface
public interface NanoClock {
    NanoClock SYSTEM = System::nanoTime;
    long now();
}
