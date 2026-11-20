package dev.dynamicchunkie.render;

/** Native section operations, called on the client thread. Implementations must never request chunks. */
public interface SectionBackend {
    @FunctionalInterface
    interface Visitor {
        /** Coordinates are block origins. False stops enumeration and makes the caller fail open. */
        boolean visit(int x, int y, int z);
    }

    boolean collect(Visitor visitor);
    boolean hasBuiltMesh(int sectionX, int sectionY, int sectionZ);
    void requestRebuild(int sectionX, int sectionY, int sectionZ);
}
