# Visual controls under development on master (26.3)

These changes are only on master. Existing version branches are unchanged. The mod
version remains 0.1.0; this is not a new published release.

## Entity distances and per-type limits

Enable `enableAdaptiveEntityDistance` for separate mob, player, dropped item, armor
stand and block-entity distances (in blocks). Only non-player distances adapt to the
smoothed frame budget; the selected player distance is retained. Glow/name safeguards
keep important entities visible. This changes rendering only, never entity ticking.

Enable `enableEntityTypeLimits` and choose per-type maximums on the additional Mod
Menu pages. Entries are sorted into Farm, Animals, Monsters, Water, End, Nether and
Misc groups. Classification is a default taxonomy; modded types fall back to their
Minecraft category. IDs such as `minecraft:cow` can also be edited in `entityTypeLimits`
in JSON. Zero is unlimited. A nearest-first selection refreshes every 250 ms; changes
can take that long to appear. Players and glowing entities bypass count limits; named
entities bypass hiding, so these protected entities may exceed a chosen count.

## Particles and clouds

`enableAdaptiveParticles` budgets ordinary typed particle creation. The default
`particleSpawnBudget` is 100 per client tick, `particleActiveLimit` is a soft limit of
4000 and `particleVisualDistance` is 64 blocks. `particleTypeBudgets` maps particle
registry IDs to per-tick budgets. Important types in `importantParticleTypes` and
Minecraft's override-limiter effects bypass cosmetic budgets, including distance.
Native particle frustum culling remains active. Direct custom `ParticleEngine.add`
calls do not pass through the typed creation budget. Already active effects are not
removed when the budget changes. These limits are not a promise of total active count.

`enableSmartClouds` reduces FANCY clouds to FAST, then OFF under sustained frame
pressure. It modifies the effective getter result, so the user's stored cloud option
is preserved. It never raises cloud quality above that selected by the user.
All these features are opt-in while in development.

## Profiler

Enable `profilerOverlay` on the visual settings page. It shows FPS, mean/p95 frame
time, measured render-thread CPU EMA, native visible-section count, extracted entity
and block-entity counts, conservative analysis-cache accounting and a 180-frame graph.
Extracted entities are not a count of GPU draw calls. Geometry VRAM usage and a true
occlusion-skipped-section count are not available and are explicitly labelled unknown.
The frame graph is sampled on the client frame hook and clamps the display at 100 ms.

## Environment analysis

The existing directional rays now retain separate up/down readings. Labels distinguish
long narrow passages, enclosed rooms, open drops and open summits. Moving players can
probe a short loaded, unobstructed route ahead for a transition to open sky; this can
slightly raise openness before leaving a cave. Fast flight into a nearby obstruction
reduces openness. These are local heuristics, not a reconstructed room map. Unknown
chunks remain blocked and are never requested by analysis.

## Remaining work

A new terrain-section occlusion implementation is not included in these changes.
Minecraft/Sodium's existing section culling remains in control. Adding a second
terrain culler and its exact skipped-section telemetry requires separate renderer work.
Particle effect importance and taxonomy require manual review with actual packs/mods.

## Verification

The build and 50 unit tests pass. Graphical suites passed with vanilla and Sodium
0.9.3-beta.1 on 26.3, including the new three-cow/two-visible selection, cosmetic
particle budget, important-particle exemption and profiler rendering regression.
Actual cloud adaptation under load, other particle providers and the category
navigation layout still need manual visual checks.
