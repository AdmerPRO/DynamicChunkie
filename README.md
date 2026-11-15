# DynamicChunkie

A client-only Fabric mod that adjusts Minecraft's Render Distance using nearby geometry.
MVP for **Minecraft 26.3**, Java 25, Fabric Loader 0.20.1 or newer, and Fabric API
0.166.2+26.3. It uses focused client mixins to retain existing render-section storage, with optional
integration for Sodium 0.9.3-beta.1+mc26.3.
It has no reflection, replacement renderer, new culling algorithm, or server component. Simulation Distance is never read or modified.

## Install and build

Install Fabric for Minecraft 26.3. Put Fabric API and
`build/libs/dynamicchunkie-0.1.0.jar` in the instance's `mods` directory.
Do not install the sources JAR. The mod version stays at **0.1.0** until explicitly changed by the user.

With JDK 25 selected:

```sh
./gradlew build
```

Windows: `gradlew.bat build`. Gradle downloads build dependencies on the first run.
`./gradlew runClient` starts a development client; `./gradlew test` runs the regression suite.

## Controls and configuration

- `/dynamicchunkie toggle`: enable/disable immediately; disabling restores the saved original distance.
- `/dynamicchunkie debug`: toggle the HUD (off by default).

The mod creates `config/dynamicchunkie.json` at startup. An example is included at
[config/dynamicchunkie.example.json](config/dynamicchunkie.example.json).
For an in-game settings screen, optionally install **Mod Menu 21.0.1** for 26.3.
Open **Mods → DynamicChunkie → Configure**. The four pages cover general switches, caves
and limits, open-terrain profiles, and timing/stability. Save validates and applies settings
immediately and writes the JSON file. Cancel/Escape discards edits; Defaults resets the
draft until Save. English and Polish labels are included. No Cloth Config is required.
Crash recovery fields are managed internally and are not exposed in the UI.
Without Mod Menu, edit the JSON while Minecraft is closed, then restart.

| Option | Default | Meaning |
| --- | --- | --- |
| enabled | true | Enable automatic distance control |
| minRenderDistance / maxRenderDistance | 2 / 32 | Hard bounds on dynamic values |
| defaultRenderDistance | -1 | Original setting; -1 captures it on first start |
| analysisIntervalMs | 750 | Sample interval, validated to 250–10000 ms |
| transitionIntervalMs | 1000 | Minimum time between RD changes, 500–10000 ms |
| targetStabilityMs | 1500 | A materially different target must persist this long, 0–10000 ms |
| preserveRenderCache | true | Retain vanilla render-section storage between RD changes |
| smoothingFactor | 0.35 | Exponential score smoothing, 0.05–1 |
| deadZoneChunks | 1 | Ignore target/current differences this small, 0–4 |
| tunnelDistance / smallCaveDistance | 2 / 4 | Enclosed-space profile values |
| largeCaveDistance / undergroundOpenDistance | 10 / 12 | Cave profile and covered-area ceiling |
| surfaceDistance | 16 | Open low-altitude ceiling |
| mountainDistance / highAltitudeDistance | 16 / 16 | Open high-altitude ceilings |
| enableAltitudeFactor | true | Gate altitude bonuses on horizontal openness |
| enableSkyDetection | true | Use loaded-column sky visibility and cave ceiling |
| enableElytraOptimization | true | Faster flight samples and movement-aware score boost |
| debugMode | false | Diagnostic HUD |
| restorePending | false | Internal crash recovery flag; do not edit |

Profile values are clamped to configured bounds. Runtime limits are taken directly from
the vanilla view-distance option's validator: normally 32, but 16 for a low-memory client.
The mod lowers the effective configured bounds accordingly. Restoring the original setting
intentionally bypasses the mod's bounds.

Manual Render Distance changes in video settings become the new restoration value.
Analysis and changes pause while a screen is open or the game is paused.
Disconnect and clean shutdown restore the original value and write Minecraft options.
Before applying dynamic values, the original is saved atomically in the mod config.
A pending restoration from a crash is recovered at the next startup with the mod installed.
Removing the mod after a crash requires manually restoring Render Distance.

## Environment algorithm

Every 750 ms, the client tick samples 26 normalized directions from the player's eyes:
eight horizontal directions, upward/downward, and sloped diagonals.
Each collision-shape ray measures the nearest obstruction up to 32 blocks. This single
distance incorporates the near and far ranges without repeating rays at 4/8/16/24 blocks.
Fluids and entities are ignored.

The normalized base score is:

```text
0.40 × (average ray length / 32)
+ 0.30 × horizontal openness
+ 0.10 × vertical openness
+ 0.10 × fraction reaching 32 blocks
+ 0.10 × sky visibility
```

Altitude adds up to 0.20, only above Y=80 and with horizontal openness above 0.55.
The final score is clamped to 0–1. Elytra sampling halves the interval (minimum 250 ms).
An additional movement-direction ray gates a small speed-related boost; enclosed flight
cannot earn a large bonus merely from speed.

Smoothed scores interpolate six configurable distance profile values at score knots
0, 0.13, 0.30, 0.65, 0.82 and 1. Covered environments cap at the underground-open value;
open sea-level terrain caps at the surface value. With sufficient horizontal openness,
the surface cap rises toward the mountain value from Y=96–160, then toward the high-altitude
value from Y=160–240. Altitude alone cannot increase the score in a tight room.
Disabling sky detection also disables the sky-based cave ceiling.

An exponential moving average, target hysteresis, integer dead zone, a target stability
window, and a minimum change interval prevent rapid oscillation. Once accepted, the target
is applied in **one abrupt change** (for example 16 → 2 or 2 → 16), without intermediate
one-chunk steps. `transitionIntervalMs` still limits change frequency. The old
`transitionSpeed` key is ignored and removed when the configuration is saved.
The HUD reports the raw score and accepted target; the latter includes smoothing,
profile ceilings and hysteresis. Environmental labels describe the raw sample.

Default surface, mountain and high-altitude profile values are all 16 chunks.
Existing saved profile values remain unchanged; use **Defaults → Save** in Mod Menu
or set `mountainDistance` and `highAltitudeDistance` to 16 to adopt these defaults.

## Minecraft integration and performance

`Options.renderDistance().set(...)` uses Minecraft's validated option and its
normal change callback. Reapplying the current value is a no-op. The mod sends standard client settings after a change so the
existing chunk-loading path receives the new distance. It never changes Simulation Distance,
server simulation settings, entity simulation, or tick code. In multiplayer, the server's
view-distance limit still applies.

World access stays on the client tick thread. There are at most 27 short raycasts per
analysis, no block-volume scans, background world access, or custom chunk requests.
Before each ray, loaded-chunk lookups check the conservative bounding rectangle of its
path. Any missing chunk rejects that ray as blocked, avoiding raycasts into unloaded terrain.
Sky detection uses the loaded column's existing heightmap. Direction vectors are reused;
vanilla raycasting still creates small temporary objects. Config writes occur at lifecycle,
command and manual-setting changes, never on each analysis or transition.

This port uses Minecraft 26.3's unobfuscated official class names, Fabric Loom 1.17.21,
and Gradle 9.6.0; Yarn mappings and JAR remapping are no longer needed.
The selected Minecraft JAR's `Options`, `OptionInstance.IntRange`, raycast APIs,
and GUI extraction APIs were inspected during the port.
See [Fabric's 26.3 migration notes](https://www.fabricmc.net/2026/09/15/263.html).
The debug HUD uses Fabric's `HudElementRegistry` and the game's `GuiGraphicsExtractor`.

The earlier 1.21.1 implementation remains in Git history. The current artifact targets
26.3 only; keep an existing `config/dynamicchunkie.json` to preserve settings across the upgrade.

## Retaining nearby chunks without full rebuilds

In vanilla 26.3, the RD comparison in `LevelExtractor.extract` calls `allChanged`.
That recreates update tracking and tells `LevelRenderer` to invalidate compiled geometry,
release all section buffers, and recreate the view area. Merely stepping the slider slowly
therefore still causes repeated full rebuilds.

With `preserveRenderCache=true`, the mod reserves vanilla render-section slots and update
tracking up to the configured maximum when a world/renderer initializes. It keeps the same
`ViewArea`, render sections and mesh buffers during distance changes within that capacity.
The engine's normal visibility traversal receives the current distance, not the reserved
capacity. Its normal asynchronous graph update retains the previous graph until the new
one is ready. This is reuse of Minecraft's rendering machinery, not a custom renderer or
a new Section/Occlusion Culling implementation.

Three mixins are necessary because Fabric has no supported event for avoiding this reset:

- `LevelRendererMixin` reserves bounded section storage at normal renderer initialization.
- `ViewAreaMixin` separates allocated capacity from the active vanilla traversal radius.
- `LevelExtractorMixin` replaces only the RD-triggered full reset with a standard graph
  invalidation, and reserves matching block-update tracking.

For example, with 2 chunks already rendered, raising RD to 8 preserves the near section
objects and their geometry. Newly needed data/geometry outside that area still loads and
compiles through Minecraft. Reducing RD retains mesh storage for potential reuse, subject
to ordinary block updates, chunk unloads and the rotating storage's eviction as you move.
This is a bounded in-memory geometry cache, not a disk/world snapshot cache.
No chunk packets are suppressed and no server-side load radius is artificially held high.

Genuine graphics/resource reloads (including F3+A), world switches and changes beyond
allocated capacity keep the normal rebuild behavior. Disabling the mod restores normal
storage handling.

### Sodium compatibility

**Tested: Sodium 0.9.3-beta.1+mc26.3** (Modrinth version
`mc26.3-0.9.3-beta.1-fabric`). Sodium is optional and is not bundled or required at runtime.

Earlier builds disabled reuse with Sodium, which allowed its separate RD-triggered
`SodiumWorldRenderer.reload()` to destroy/recreate its section manager on each step.
This build handles that renderer separately: it changes the existing manager's distance,
uses Sodium's own expandable section tree, and marks its graph dirty. The section manager,
regions, mesh buffers and existing section fade timestamps remain alive. Section-time
buffer capacity for the vanilla 2–32 range is reserved once at renderer initialization; Sodium still handles any
necessary later growth. Genuine F3+A/resource reloads continue to rebuild normally.

The Sodium integration is loaded only for the exact tested version. With another Sodium
version, or with reuse disabled while Sodium is present, automatic RD changes are paused
and the original setting is restored instead of repeatedly resetting the renderer.
The debug HUD identifies `Sodium (reuse)` or `Sodium: auto RD paused`.
Other replacement renderers and Iris/shaders remain untested.

Reserved slots increase memory use up to the configured maximum, though they do not
force meshes to compile or chunks to load to that distance. A larger maximum can retain
more geometry and consume more RAM/VRAM. New terrain generation, downloads and compilation
can still cause frame-time spikes; this change does not promise zero lag.

## Validation and manual acceptance checks

Fourteen unit tests cover relative tunnel/cave/surface scoring, altitude gating, continuous
profile interpolation, environment ceilings, nonfinite/invalid settings, configured bounds,
target stability under doorway oscillation, isolated UI drafts and restoration metadata preservation.
Compilation validates the selected Minecraft and Fabric APIs.
The Minecraft 26.3 development client was launched successfully with Fabric Loader 0.20.1,
Fabric API 0.166.2+26.3, and this mod. Startup reached the GUI, the client tick initialized
the mod configuration, and the user confirmed that Minecraft opened. On this machine the
game fell back from OpenGL to Vulkan; missing narrator libraries and development-account
Realms authentication produced unrelated warnings. The automated client-world regression also passed: repeated 2/8/2/4 transitions preserved
the same nearby section objects and compiled meshes, block changes still rebuilt geometry,
a genuine reload recreated storage, and Simulation Distance remained unchanged.
The Sodium + Mod Menu client test also passed with the exact versions above: repeated
RD changes retained the section manager, near sections, visibility data and mesh storage;
every sampled transition frame retained visible terrain. Block updates and genuine reloads
still worked and Simulation Distance stayed unchanged. The Mod Menu provider was discovered,
its screen opened, and edited settings were saved/applied successfully.
Visual smoothness with the user's world/settings, long-flight memory usage and the following
environmental checks remain manual:

1. Set RD to 18 and Simulation Distance to 7; join a world and enable the debug HUD.
2. Enter a long 1×2 tunnel. Wait 15–20 seconds; target/current should fall toward 2–4.
3. At similar Y, enter a broad cave with 16–32 blocks of clearance. Target should rise
   toward 8–12. This distinguishes spaces at the same altitude.
4. Move onto open sea-level terrain. Target should approach 12–16; move onto an open
   mountain around Y=160, then above Y=240; the default open-terrain target remains 16.
5. Enter a sealed small room at Y=240. Target must decrease despite altitude.
6. Walk repeatedly through a cave entrance. Confirm score smoothing, the one-chunk dead
   zone, target stability window, and no more than one RD change per transition interval.
   Each accepted target must be reached in one RD change, without intermediate values.
7. Fly Elytra over open terrain, then through a safe enclosed passage. Check increased
   sample frequency, open-flight growth and enclosed-flight reduction.
8. Restart with min=6/max=12. Check all dynamic values remain within 6–12 after the initial
   transition interval. Repeat with invalid values and confirm normalized config.
9. Toggle the mod off. RD must return to 18. Repeat with disconnect, clean quit/restart,
   manual RD changes, dimension changes, and a crash of a disposable test instance.
10. Check Simulation Distance remains 7 throughout, including in `options.txt`; test
    both singleplayer and a remote server.
11. Test near unloaded chunk boundaries and during teleportation. Missing data must not
    cause large open-space targets or explicit chunk loads.

For the cache regression, run `./gradlew runClientGameTest` with a graphical session.
The separate test mod creates a disposable world; it is not included in the release JAR.
It checks section/mesh identity during repeated distance changes, unchanged Simulation
Distance, correct mesh refresh after block changes, and the continued ability to perform a genuine renderer reload.
Unit tests also cover target stability and oscillation at cave entrances.
Run `./gradlew runClientGameTest -PwithSodium` for the Sodium 0.9.3-beta.1 + Mod Menu 21.0.1
integration suite; `./gradlew runClient -PwithSodium` starts that development setup.
The property adds these mods to the development runtime only; neither is bundled.

Additional manual checks: repeatedly leave/re-enter the same tunnel while watching the
debug HUD's reuse counter; nearby blocks should remain visible during RD transitions.
Break/place blocks, alter lighting, cross chunk boundaries, teleport and switch dimensions
to check that retained geometry still updates. Profile frame times and memory over a long
Elytra flight and compare cache on/off. Repeat on a remote server.

## Known limitations

- 32-block local samples cannot infer distant horizons, all room shapes, or nearby cave exits.
  Glass uses collision geometry; foliage, water, roofs, and overhangs can influence classification.
- Unknown chunks are conservatively blocked. Chunk arrival can temporarily lower targets,
  especially directly after joining or teleporting.
- Profile values are heuristics, not measured visibility guarantees. There is no FPS feedback,
  predictive loading, Section Culling, Occlusion Culling, or custom rendering.
- Genuine renderer reloads and arrival/compilation of new chunks can still cause stutter.
  Retaining near geometry avoids the RD-triggered full rebuild; it does not remove loading costs.
- Other mods controlling the same option can conflict. Only the exact Sodium version listed
  above has a tested integration; Iris/other renderers and expanded-distance mods remain untested.
- No live external-file reload. Use Mod Menu for live validated edits, or edit the file offline.

## GitHub Actions

[Build](.github/workflows/build.yml) runs on pushes, pull requests and manual dispatch.
It validates the Gradle wrapper and builds/tests with Temurin Java 25 on Linux and Windows.
Each successful job uploads the mod and sources JARs; unit test reports are uploaded even
when tests fail. Download them from **Actions → Build → run → Artifacts**. Artifacts are
retained for 14 days. The workflow does not change the mod version or publish releases.

[Client integration tests](.github/workflows/client-tests.yml) is manually triggered from
**Actions → Client integration tests → Run workflow**. Its two jobs exercise vanilla and
the supported Sodium + Mod Menu setup using Xvfb and Mesa software Vulkan on Ubuntu 24.04.
Client logs, crash reports, screenshots and reports are uploaded for diagnosis.
Both workflows use dependency caching and require no configured secrets.

Workflow syntax and expressions were checked locally with actionlint. Execution on GitHub's
Windows runners and software Vulkan environment must be confirmed on the first hosted run;
local client tests were previously run with the development machine's GPU.

## Author and license

Author: **AdmerPRO**.

Copyright (C) 2026 AdmerPRO. Licensed under **GNU General Public License version 3 only**
(`GPL-3.0-only`); see [LICENSE](LICENSE). The complete license is included in the binary
and sources JARs. Sodium and Mod Menu remain separately distributed projects with their
own licenses.
