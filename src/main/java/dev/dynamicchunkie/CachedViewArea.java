package dev.dynamicchunkie;

/** Access only to vanilla storage capacity and the radius used by its own visibility traversal. */
public interface CachedViewArea {
    int dynamicchunkie$capacity();
    boolean dynamicchunkie$isManaged();
    void dynamicchunkie$setVisibleRadius(int radius);
}
