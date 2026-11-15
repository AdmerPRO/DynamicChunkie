# DynamicChunkie

A client-only Fabric mod that adjusts Minecraft's Render Distance using nearby geometry.
MVP for **Minecraft 26.3**, Java 25, Fabric Loader 0.20.1 or newer, and Fabric API
0.166.2+26.3. It has no mixins, reflection, replacement renderer, culling implementation,
or server component. Simulation Distance is never read or modified.

## Install and build

Install Fabric for Minecraft 26.3. Put Fabric API and
`build/libs/dynamicchunkie-0.2.0+26.3.jar` in the instance's `mods` directory.
Do not install the sources JAR.

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
Edit the file while Minecraft is closed, then restart.

| Option | Default | Meaning |
| --- | --- | --- |
| enabled | true | Enable automatic distance control |
| minRenderDistance / maxRenderDistance | 2 / 32 | Hard bounds on dynamic values |
| defaultRenderDistance | -1 | Original setting; -1 captures it on first start |
| analysisIntervalMs | 750 | Sample interval, validated to 250–10000 ms |
| transitionIntervalMs | 1000 | Minimum time between RD changes, 500–10000 ms |
| transitionSpeed | 2.0 | Chunks per second, 0.25–8 |
| smoothingFactor | 0.35 | Exponential score smoothing, 0.05–1 |
| deadZoneChunks | 1 | Ignore target/current differences this small, 0–4 |
| tunnelDistance / smallCaveDistance | 2 / 4 | Enclosed-space profile values |
| largeCaveDistance / undergroundOpenDistance | 10 / 12 | Cave profile and covered-area ceiling |
| surfaceDistance | 16 | Open low-altitude ceiling |
| mountainDistance / highAltitudeDistance | 24 / 32 | Open high-altitude ceilings |
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

An exponential moving average, target hysteresis, integer dead zone, bounded transition
steps, and a minimum change interval prevent rapid oscillation. The HUD reports the raw
score and accepted target; the latter includes smoothing, profile ceilings and hysteresis.
Environmental labels are approximate descriptions of the raw sample.

## Minecraft integration and performance

`Options.renderDistance().set(...)` uses Minecraft's validated option and its
normal change callback. The mod sends standard client settings after a change so the
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

## Validation and manual acceptance checks

Automated tests cover relative tunnel/cave/surface scoring, altitude gating, continuous
profile interpolation, environment ceilings, nonfinite/invalid settings, and configured bounds.
Compilation and remapping validate the selected Minecraft and Fabric APIs.
The Minecraft 26.3 development client was launched successfully with Fabric Loader 0.20.1,
Fabric API 0.166.2+26.3, and this mod. Startup reached the GUI, the client tick initialized
the mod configuration, and the user confirmed that Minecraft opened. On this machine the
game fell back from OpenGL to Vulkan; missing narrator libraries and development-account
Realms authentication produced unrelated warnings. No in-world acceptance test was performed.
The following checks remain manual:

1. Set RD to 18 and Simulation Distance to 7; join a world and enable the debug HUD.
2. Enter a long 1×2 tunnel. Wait 15–20 seconds; target/current should fall toward 2–4.
3. At similar Y, enter a broad cave with 16–32 blocks of clearance. Target should rise
   toward 8–12. This distinguishes spaces at the same altitude.
4. Move onto open sea-level terrain. Target should approach 12–16; move onto an open
   mountain around Y=160, then above Y=240, to approach 20–24 and 24–32.
5. Enter a sealed small room at Y=240. Target must decrease despite altitude.
6. Walk repeatedly through a cave entrance. Confirm score smoothing, the one-chunk dead
   zone, and no more than one RD change per transition interval. Default steps are about two chunks.
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

## Known limitations

- 32-block local samples cannot infer distant horizons, all room shapes, or nearby cave exits.
  Glass uses collision geometry; foliage, water, roofs, and overhangs can influence classification.
- Unknown chunks are conservatively blocked. Chunk arrival can temporarily lower targets,
  especially directly after joining or teleporting.
- Profile values are heuristics, not measured visibility guarantees. There is no FPS feedback,
  predictive loading, Section Culling, Occlusion Culling, or custom rendering.
- Existing renderer rebuilds and chunk arrival can still cause stutter when distance changes.
  Transition controls reduce frequency but cannot eliminate vanilla loading costs.
- Other mods controlling the same option can conflict. Compatibility with Sodium/Iris and
  expanded-distance mods has not been tested; this MVP uses the vanilla validator.
- No settings GUI or live file reload. Commands persist immediately; edit other settings offline.
