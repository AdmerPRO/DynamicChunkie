# Loaded-chunk ray guard

The guard walks the segment's X/Z projection through the 16-block chunk grid using
Amanatides-Woo DDA. It queries only the start/end owners and the chunks entered along
the path, in traversal order. It does not query the entire bounding rectangle.

A simultaneous X/Z boundary crossing advances both coordinates, omitting off-ray
corner neighbours. Nearly coincident crossings are not merged using an epsilon: even
a short positive-length segment through a neighbouring chunk must be checked.
Positions use floor division, including negative coordinates. Start and end points
belong to their half-open chunk cells. Reached endpoint axes are disabled to avoid
floating-point accumulation overshooting an exact endpoint boundary.

The callback only reads chunk availability. A missing chunk on the actual path still
rejects the ray; chunks outside the path do not affect the result. The existing native
block raycast and unknown-terrain policy remain in use. One callback is bound per
analysis and reused for every direction, rather than allocated for every ray.

Regression coverage includes diagonal/corner paths, very thin intermediate chunks,
negative coordinates, axis-aligned and vertical rays, exact endpoint boundaries,
invalid coordinates, missing start/end chunks and 5,000 seeded segments compared
against independent segment/rectangle intersection tests.
