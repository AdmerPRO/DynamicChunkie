package dev.dynamicchunkie.render;

/** Native section operations, called on the client thread. Implementations must never request chunks. */
public interface SectionBackend {
    @FunctionalInterface
    interface Visitor {
        /** Coordinates are block origins. False stops enumeration and makes the caller fail open. */
        boolean visit(int x, int y, int z);
    }

    boolean collect(Visitor visitor);
    long geometryCost(int sectionX,int sectionY,int sectionZ);
    default boolean hasBuiltMesh(int x,int y,int z) {return geometryCost(x,y,z)>=0;}
    void requestRebuild(int sectionX, int sectionY, int sectionZ);
}
