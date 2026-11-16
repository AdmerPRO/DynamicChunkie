# DynamicChunkie — Minecraft 1.21.7

**Minecraft 1.21.7**, Java 21, Fabric Loader 0.20.1+, Fabric API **0.129.0+1.21.7**.
Author: **AdmerPRO**. Version: **0.1.0**. License: **GPL-3.0-only**.
This artifact targets 1.21.7 specifically. [Branch matrix](PORTS.md).

## Install and build

Build: ./gradlew build (Windows: gradlew.bat build).
Install build/libs/dynamicchunkie-mc1.21.7-0.1.0.jar and Fabric API in mods.
Do not install the sources JAR.

Optional Sodium: **mc1.21.8-0.7.3-fabric**. Optional Mod Menu: **15.0.2**.
Neither is bundled. No Cloth Config dependency.
Development client: ./gradlew runClient, or add -PwithSodium.
Publisher labels the 1.21.5 Sodium build as beta.

## Settings

Open Mods → DynamicChunkie → Configure in Mod Menu. Five pages cover general options,
cave/terrain profiles, timing and entity culling. Save validates and applies edits;
Cancel discards them; Defaults resets the draft until saved. Polish/English labels.

Without Mod Menu, edit config/dynamicchunkie.json offline.
[Example configuration](config/dynamicchunkie.example.json).
Commands: /dynamicchunkie toggle and /dynamicchunkie debug.

Defaults: min RD 2, max RD 32; tunnel 2, small cave 4, large cave 10,
covered open area 12, surface/mountain/high altitude 16. Analysis 750 ms,
target stability 1500 ms, dead zone 1 chunk, minimum transition interval 1000 ms.
Accepted targets apply abruptly without intermediate one-chunk steps.
Saved custom values remain unchanged; choose Defaults to adopt these defaults.
Internal restoration fields are not exposed in the settings screen.

Entity culling defaults on: 16 entities per tick and a 64-block check radius.
Budget validates to 1–64; distance to 8–96 blocks.

## Algorithm and renderer integration

26 eye-position rays sample horizontal, vertical and diagonal directions up to 32 blocks.
Openness combines mean ray distance (40%), horizontal clearance (30%), vertical clearance
(10%), clear-ray fraction (10%) and sky visibility (10%). Altitude/Elytra bonuses require
open geometry. Score smoothing, stability and hysteresis limit entrance oscillation.
Configurable profile interpolation and environment ceilings produce the bounded target.
Unknown chunks are conservative and never requested for analysis.

The mod changes the native Options.renderDistance option and sends normal client settings.
It preserves the original value before dynamic changes and restores it on disable,
disconnect/shutdown, including pending crash recovery when the mod remains installed.
Manual changes in video settings become the new restore point.

Focused vanilla renderer mixins reserve section capacity and intercept only the
distance-triggered full reset. Nearby section objects and mesh buffers remain alive.
Sodium has a separate version-specific path updating its existing manager and graph.
Normal block updates and genuine F3+A/resource reloads still rebuild.
Other Sodium versions suspend automatic RD rather than repeatedly resetting the renderer.

Entity culling is budgeted client-tick work. A center ray plus eight rays to an expanded
bounding box must all hit loaded opaque full blocks in two samples before rendering
is skipped. Glass, partial blocks, unknown chunks, motion and stale samples stay visible.
Players, named/glowing entities, mounted entities, large models and objects within
8 blocks are exempt. Only living entities and dropped items are targeted.
An installed EntityCulling mod disables this culler to avoid overlapping control.
Rendering only reads cached decisions. Simulation Distance, AI, entity ticks,
networking and server simulation are unchanged.

## Validation

20 unit tests cover scoring, bounds, draft isolation, target stability and voxel traversal.
Build/unit tests run on Linux and Windows in GitHub Actions.
Manual client workflow is also available. Local client check:
./gradlew runClientGameTest -PwithSodium

The client regression covers RD/mesh preservation, block updates, genuine reloads,
Mod Menu saves, abrupt RD changes, and stone/glass/glowing entity visibility.
See PORTS.md for completed local world runs.

Manual checks: compare tunnel/cave at the same Y, surface/high terrain and an enclosed
high room. Test Elytra in open/enclosed space. Watch a mob behind stone/glass and while
glowing. Break/place blocks, teleport and switch dimensions. Toggle the mod and verify
restoration. Confirm Simulation Distance is unchanged. Test other model/renderer mods.

## Limits

Local samples cannot measure entire horizons or all small apertures. Entity occlusion
is approximate; disable it for inaccurate modded bounds. Retained geometry costs memory.
New terrain/downloads/mesh builds may stutter. Genuine renderer reloads clear the cache.
No custom renderer, terrain Section Culling, live external-file reload or universal shader
compatibility is provided.

Copyright (C) 2026 AdmerPRO. See [LICENSE](LICENSE).

See [adaptive controls and transparent loading](docs/adaptive-controls.md).
