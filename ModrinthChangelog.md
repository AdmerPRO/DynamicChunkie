# Changelog

## DynamicChunkie v0.1.0 — First release

The first release of DynamicChunkie introduces the client-side dynamic Render Distance system and its initial supported Minecraft ports.

### Features

- Analyzes nearby space with multi-directional raycasts, including horizontal, vertical and diagonal directions.
- Uses chunk-grid DDA to check only loaded chunks crossed by each ray; unloaded chunks are not loaded by the analysis.
- Estimates openness from ray distances, clear rays, horizontal and vertical visibility, view direction, sky access and altitude.
- Uses optional velocity and look-direction prediction during fast Elytra flight.
- Provides configurable environment targets, minimum/maximum distance, score smoothing, hysteresis and a dead zone.
- Limits normal increases to up to 2 chunks per second. After teleporting or changing dimensions, starts at the minimum and increases by 1 chunk per second; recent combat bypasses gradual recovery.
- Adapts analysis frequency to stationary, walking and Elytra movement, with a bounded cache invalidated by world changes.
- Adapts its distance ceiling to sustained frame-time and render-thread CPU pressure, and uses GPU timing when available.
- Reuses nearby vanilla or supported Sodium render geometry across distance changes.
- Adds optional entity occlusion culling with safeguards for players, glowing entities and transparent blocks.
- Adds a translucent loading progress bar and allows movement and block interaction during resource reloads when the world is ready.
- Adds an optional Mod Menu configuration screen and debug HUD.
- Preserves the original Render Distance and restores it when the mod is disabled.
- Keeps Simulation Distance and server simulation unchanged.

### Supported Minecraft versions

26.3, 26.2, 26.1.2, 26.1.1, 26.1, and 1.21.1 through 1.21.11. Use the build for the exact Minecraft version.
### Development additions on master — Minecraft 26.3 only

- Conservative 16×16×16 Terrain Section Culling for vanilla and the supported Sodium build,
  with bounded work, block/chunk invalidation and command-cache restoration.
- Separate adaptive mob, player, item, armor stand and block-entity distance controls.
- Per-type nearest-first entity limits and category navigation, with optional name/glow protection.
- Per-type cosmetic particle budgets, importance controls and adaptive cloud quality.
- A profiler with frame-time history, CPU/GPU timing, render-input counts and cache accounting.
- Directional room/passage/drop/summit classification, verified cave-exit prediction,
  and faster downward RD response when flying toward a known obstacle.

Version remains 0.1.0. Other supported Minecraft branches have not received these additions.
