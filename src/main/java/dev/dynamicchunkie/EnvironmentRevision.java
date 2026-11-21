package dev.dynamicchunkie;

import dev.dynamicchunkie.render.RenderChanges;

public interface EnvironmentRevision {
    RenderChanges dynamicchunkie$changes();
    default long dynamicchunkie$revision() { return dynamicchunkie$changes().geometry(); }
}
