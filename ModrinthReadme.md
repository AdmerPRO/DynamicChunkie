# DynamicChunkie

DynamicChunkie is a client-side Fabric mod that adjusts Minecraft's Render Distance to the space around you. Narrow tunnels can use a short distance, while open caves and landscapes can use more. It also includes entity occlusion culling and keeps nearby rendered geometry when the distance changes.

**Current release: v0.1.0 - first release**  
**Author:** AdmerPRO · **License:** GNU GPL v3.0 (GPL-3.0-only) · **Client-side only**

## Supported Minecraft versions

Use the file built for your exact Minecraft version. The branches and artifacts target these releases:

- Minecraft **26.3**, **26.2**, **26.1.2**, **26.1.1**, **26.1**
- Minecraft **1.21.1–1.21.11**, with a separate target for each patch version

## What it does

- Adjusts client Render Distance from nearby geometry, not just player altitude.
- Samples forward, backward, left, right, up, down and diagonal directions. It accounts for openness, sky visibility, altitude and the direction you are facing.
- Predicts a little ahead during fast Elytra flight using movement and look direction.
- Smooths the environment score and uses a dead zone to limit unnecessary changes.
- Normally increases distance by up to 2 chunks per second. After a teleport or dimension change, it starts at the configured minimum (at least 2) and increases by 1 chunk per second. Recent combat skips or ends the gradual recovery.
- Adjusts its analysis interval to movement: typically 1500 ms while stationary, 750 ms while walking and 250 ms during Elytra flight. A bounded cache reuses recent analysis when the position and view have not changed and the world has not been edited.
- Uses frame time, available GPU timing and render-thread CPU time to lower the distance ceiling under sustained load and raise it gradually when there is headroom.
- Keeps nearby vanilla or supported Sodium render geometry alive across Render Distance changes.
- Can show a debug HUD with openness, direction, analysis timing, cache use and performance data.
- Shows a translucent progress bar during world/resource loading. During an in-world resource reload, gameplay input can continue when the player and their chunk are ready.
- Provides an optional Mod Menu settings screen.

The normal renderer and chunk-loading system remain in use. The mod does **not** change Simulation Distance, server simulation or tick behavior, and does not replace the renderer.

## Install

1. Install Fabric Loader for your Minecraft version.
2. Add the matching Fabric API build to the instance's `mods` folder.
3. Add the DynamicChunkie JAR for that exact Minecraft version.

## Settings and commands

Configuration is saved as `config/dynamicchunkie.json`. A documented example is in [config/dynamicchunkie.example.json](config/dynamicchunkie.example.json). You can set minimum and maximum Render Distance, environment targets, analysis/cache limits, performance adaptation, teleport recovery and loading-screen behavior. Values are validated and the maximum is limited to what the Minecraft client supports. Defaults include a 2–32 chunk range and 16 chunks for open, mountainous and high-altitude areas.

With Mod Menu installed, open **Mods → DynamicChunkie → Configure**. Changes apply when saved. Without Mod Menu, edit the JSON while Minecraft is closed and restart.

- `/dynamicchunkie toggle` enables or disables automatic distance control. Disabling restores the saved original Render Distance.
- `/dynamicchunkie debug` toggles the debug HUD, which is off by default.

## How the environment analysis works

The client casts a small set of rays from near the player's eyes, at several distances. A chunk-grid DDA checks that each ray only reads chunks it actually crosses; unloaded chunks are treated as blocked and are never requested. Ray distances and clear-ray counts estimate horizontal and vertical openness. Directional measurements distinguish an open view ahead from a wall behind. Sky visibility and altitude refine the score, while altitude alone cannot classify an enclosed room as open.

The combined openness score is smoothed, mapped to configurable environment targets and constrained by the configured minimum and maximum. Elytra prediction can adjust the target slightly when the path ahead is loaded and open. Analysis runs on the client tick thread at an adaptive interval, not every frame.

Render Distance is changed through Minecraft's validated client option and its normal callbacks. The original value is saved for restoration. When supported cache reuse is active, vanilla or Sodium retains nearby geometry while adjusting its visible radius. The mod does not alter Simulation Distance.

## Performance and limitations

The analysis cache and its memory accounting are bounded. The memory budget is a soft reservation estimate for section slots plus a separate limit for analysis results; it is **not** a hard cap on Minecraft/Sodium process memory or GPU VRAM. The game renderer owns geometry allocation and release. GPU utilization is used only when the game's timing data is available.

Environment samples cannot know every distant horizon or cave opening. Unloaded chunks count as blocked. Resource pack reloads and compiling newly visible chunks can still cause frame-time spikes. 

## New visual controls — Minecraft 26.3 only

The master build adds optional conservative Terrain Section Culling, separate entity
and block-entity distances, nearest-first per-type population limits with categories,
particle budgets and importance rules, adaptive cloud quality, improved cave/exit/summit
analysis, and a profiler with frame history and actual extra terrain-skip counters.
Enable the controls in Mod Menu. These additions are not in the other version ports.
The terrain culler uses verified fully opaque sections and fails open on uncertain data;
see the project documentation for its limits and supported renderer combinations.

An additional experimental hot/soft section mode keeps visible 16×16×16 sections
and a one-section neighbour buffer warm, including diagonal neighbours. It can
defer safe offscreen rebuilds while retaining existing meshes. It is off by
default and currently available only on 26.3/master. Automated OFF/ON benchmarks
record frame times and extra draw skips; neither a fixed FPS improvement nor
RAM/VRAM savings is guaranteed. See [benchmark instructions](docs/hot-soft-benchmarks.md).
