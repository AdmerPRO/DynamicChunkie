# Visual controls on master (Minecraft 26.3)

These additions are implemented only on master. Other version branches and their
artifacts are unchanged. The mod version is still 0.1.0; no release has been published
by this work. New visual controls are opt-in through Mod Menu or JSON.

## Terrain Section Culling

Enable `enableTerrainSectionCulling` on the terrain page. The culler filters actual
16×16×16 geometry-section draw inputs in vanilla and the supported Sodium build.
It supplements their existing frustum/connectivity culling and keeps their meshing,
visibility graphs, sorting, block updates and rendering pipelines in place.

A single verified opaque section must cover all eight corners of an inflated target
box before the target is skipped. The occluder box is slightly inset and the target
is inflated by two blocks. Partial coverage, unknown chunks, nearby camera sections,
transparent/partial block sections and camera positions inside an occluder fail open.
This deliberately leaves many potentially hidden sections to the native renderer.
It does not combine sparse walls or uncertain samples into an occlusion decision.

Discovery reads actual state frequencies from loaded section palettes, not block-volume
world queries. It visits at most 16 section palettes per client tick with a 1 ms soft
work budget, within eight sections of the camera, retaining up to 128 opaque boxes.
A palette can retain unused AIR entries; only positive state counts affect opacity.
Minecraft's `isSolidRender` and `canOcclude` flags supply the opacity classification.
New occluders wait for native render queues to finish and one second of stable world
data, so newly placed blocks cannot hide terrain before their meshes are available.

Camera-position changes invalidate decisions. Local section and chunk stamps invalidate the occluder snapshot when its search neighbourhood changes. Fixed-size stamp tables can also cause conservative invalidation on a hash collision. Resource/renderer reloads
also invalidate it. Unknown/unverified data is rendered. Work is bounded by
`terrainChecksPerFrame` (default 128, validated 8–1024), a 1 ms soft geometry-test budget
per frame, and 8192 cached decisions. Unexamined candidates remain visible.

Vanilla receives a filtered copy of its draw input; its real visible-section list is
preserved. Sodium geometry iterators preserve ordering. Its cached command batches are
refreshed when the camera, decision generation or world changes; compiled meshes stay
alive. Stationary batches are reused, and disabling this feature restores ordinary batch
reuse. Command-cache restoration is independent of the RD geometry-reuse option. Weak region tracking avoids keeping unloaded regions alive. The culler is bypassed
in spectator mode and with Iris, whose alternate shadow viewpoints need another adapter.
Custom models extending beyond the inflated box or changing opaque-block assumptions
need compatibility checks. This is conservative culling, not a promise to remove every
invisible section or to improve FPS in every scene.

## Entity distances and chosen type limits

Enable `enableAdaptiveEntityDistance` for separate mob/fallback, player, dropped item,
armor stand and block-entity distances in blocks. Non-player distances adapt gradually
to the frame budget; the chosen player distance is retained. A user FPS cap counts as
the target, not as overload. Entity ticking, networking and simulation are untouched. These are upper limits; native visibility and server tracking can restrict distance further.

Enable `enableEntityTypeLimits` and choose maximums by registry ID, for example
`minecraft:cow: 100` as the JSON map entry `"minecraft:cow": 100`. Zero is unlimited.
Mod Menu groups types into Farm, Animals, Monsters, Water, End, Nether and Misc and
provides category jump buttons. Modded types use their Minecraft category as fallback.
The nearest eligible entities are kept first; the scheduler checks for changed membership, camera position or model budgets at a nominal 250 ms interval. Stationary candidates reuse the previous selection.
Players are not subject to these population budgets.

`protectImportantEntities` defaults to true, exempting named/glowing entities from the
visual budgets. Disable it if those entities should count toward strict type limits too.
The local player remains protected. Draft editing copies maps and priority sets, so
Cancel cannot accidentally modify active limits.

## Adaptive Particle System and Smart Visual Quality

`enableAdaptiveParticles` budgets ordinary typed particle creation. Defaults:
100 cosmetic spawns per tick, soft active limit 4000, maximum cosmetic distance 64
blocks. Performance pressure reduces cosmetic budgets/range. Per-type budgets and
importance can be edited in Mod Menu, or through `particleTypeBudgets` and
`importantParticleTypes` in JSON. A zero type budget disables that cosmetic type.
Minecraft override-limiter effects remain important and their UI toggle is locked.
Important effects bypass cosmetic count and distance limits. Native particle frustum
culling still handles visibility. Existing effects are not abruptly deleted.
Direct custom `ParticleEngine.add` calls bypass the typed-creation policy, and important
effects can exceed the soft active limit; this is not a hard global particle cap.

`enableSmartClouds` reduces FANCY clouds to FAST, then OFF under sustained frame pressure.
It changes the effective getter result without overwriting the stored cloud option.
Quality never exceeds the user's selection and returns when headroom recovers. Distant
particle and entity budgets provide the optional reduction of distant visual elements.
No low-poly terrain, texture mip replacement or custom renderer is introduced.

## Performance Profiler

Enable `profilerOverlay` on the visual page. It shows:

- Native FPS and measured frame-time mean/p95.
- Render-thread CPU EMA and GPU timing when Minecraft supplies it.
- Geometry-section candidates remaining after the additional filter and unique extra
  sections skipped, including sections omitted by reused Sodium command batches.
- Extracted entity and block-entity counts.
- Conservatively charged analysis/terrain-cache memory.
- A 180-frame history graph, displayed in the 0–100 ms range.

Terrain counters are latched from completed frames and deduplicated across terrain passes.
Section counts describe render input, not GPU draw calls or all native occlusion rejects.
Entity counts describe extracted render states, not simulated entities or driver calls.
Geometry VRAM and total engine cache allocation are not measured. Cache bytes are soft
accounting estimates. With the profiler enabled, frame summaries keep updating even
when automatic distance control is disabled, allowing manual A/B comparisons.

## Predictive distance and Smart Cave Detection 2.0

Directional samples keep separate up/down measurements. Relative openness along and
across the player distinguishes long narrow passages, enclosed rooms and large covered
areas. A long downward view indicates an open drop; broad horizontal/upward space at
altitude can identify an open summit. Height alone never opens an enclosed room.

A short probe ahead uses movement and gaze, checks a loaded/unobstructed route, and
samples future openness and sky. A verified transition to sky can increase the score
before leaving a cave; opening terrain ahead can also raise it before a summit. Fast
Elytra movement toward a known obstruction bypasses the normal downward smoothing and
stability delay for a lower environment target. It never raises the target in that case.
A zero/unknown movement ray is not treated as proof of a wall. Configured bounds and
normal gradual distance growth remain enforced. These are local shape heuristics,
not a complete room map or custom predictive chunk loading.

## Verification

The build and 61 unit tests pass. Full graphical suites run with vanilla and Sodium
0.9.3-beta.1 on 26.3. They cover native mesh reuse, block updates, restoration metadata,
entity occlusion, walking/breaking blocks during resource reloads, cow count and mob
distance budgets, important/per-type particle rules, saving particle settings through
Mod Menu, profiler rendering, a loaded cave exit, and real terrain draw skips.
Terrain tests remove an opaque section while the camera stays still, verify immediate
reappearance, reject a glass section as an occluder, and verify disabled behavior.
Sodium tests also ensure inactive terrain culling does not invalidate command batches
when the camera moves. Geometry tests include negative coordinates and randomized
interior-point checks of accepted box shadows. Unit tests exercise cloud degradation,
FPS cap handling and urgent downward target changes without admitting increases.

Long sessions, real GPU/CPU saturation, resource packs changing block opacity/models,
and arbitrary modded particles still require manual validation and profiling.

## Renderer budgets in Mod Menu

The Performance tools pages include these optional controls.

| Setting | Default | Effect |
|---|---:|---|
| `entityShadowDistance` | 0 | Limit native entity shadow extraction beyond this many blocks. |
| `entityNameDistance` | 0 | Limit distant labels while preserving player names, glowing entities and explicitly visible names. |
| `mobDetailDistance` | 0 | Limit the sheep wool undercoat beyond the selected distance. |
| `entityTypeCpuBudgetMicros` | 0 | Set a client CPU submission budget per explicitly limited entity type. The selector keeps the nearest models and applies the configured maximum. |
| `shortenAmbientParticles` | false | Under load, halve the lifetime of distant ash, white ash and spore blossom air. |

Zero preserves the native detail distances and disables CPU cost sampling. A positive CPU budget samples one model submission per 32 calls of each configured type and requires eight observations before adjusting its limit. These timings measure CPU command submission. GPU execution is asynchronous.

Cosmetic admission runs before the native particle provider constructs the effect. Under sustained bursts it spaces admissions using the preceding tick's demand. Region and group quotas leave room for effects from other sources. Direct additions of existing particle instances bypass provider admission. Clouds require one second of sustained overload per downgrade and five seconds of recovery per upgrade.

The client test constructs a stone tunnel, a roofed chamber and an open platform at Y=180. It samples the same player position and direction in all three and checks the order of the measured openness scores.
