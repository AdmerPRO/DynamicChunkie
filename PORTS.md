# Branches and validation

Version **0.1.0** on every branch. Author: **AdmerPRO**.
License: **GPL-3.0-only**. No Simulation Distance/server simulation changes.

| Branch | Minecraft | Java |
| --- | --- | --- |
| master | 26.3 | 25 |
| 26.2 | 26.2 | 25 |
| 26.1.2 | 26.1.2 | 25 |
| 26.1.1 | 26.1.1 | 25 |
| 26.1 | 26.1 | 25 |
| 1.21.1 | 1.21.1 | 21 |
| 1.21.2 | 1.21.2 | 21 |
| 1.21.3 | 1.21.3 | 21 |
| 1.21.4 | 1.21.4 | 21 |
| 1.21.5 | 1.21.5 | 21 |
| 1.21.6 | 1.21.6 | 21 |
| 1.21.7 | 1.21.7 | 21 |
| 1.21.8 | 1.21.8 | 21 |
| 1.21.9 | 1.21.9 | 21 |
| 1.21.10 | 1.21.10 | 21 |
| 1.21.11 | 1.21.11 | 21 |

Each branch selects exact Minecraft/Fabric API/Sodium/Mod Menu dependencies.
A JAR for one target does not claim compatibility with every 1.21.x patch.
Adjacent releases may share APIs; separate artifacts keep dependency selection explicit.
All branches include entity culling, dynamic RD, geometry reuse, Mod Menu, and the
default 16-chunk high-altitude profile.

Build/unit tests pass on all targets. World regressions cover 26.3, 1.21.11,
1.21.10, 1.21.9, 1.21.8, 1.21.7, 1.21.6 and 1.21.5. Older releases use startup checks with Sodium because the
client game-test API used here is unavailable. See each branch's README for commands.

## Adaptive/loading update (2026-11-16)

Every target includes chunk-grid DDA, directional/predictive visibility, adaptive
analysis with bounded caching, frame-time/CPU/optional GPU budgets, soft cache
reservation accounting, teleport/combat recovery and translucent loading bars.
Normal growth defaults to +2 chunks/s; teleport recovery is +1 chunk/s from the
configured minimum. See [policy details and limitations](docs/adaptive-controls.md).

The baseline 48-unit suite passes per target; master now has additional tests. With Sodium/Mod Menu, 26.3 and 1.21.5–1.21.11
pass real-world regressions for geometry retention, block updates, entity visibility,
movement/block breaking during an unfinished reload, and native completion callbacks.
1.21.1–1.21.4 pass startup checks; their in-world loading/input behavior still requires
manual validation because this client game-test API is unavailable there.
The 26.3, 1.21.5 and 1.21.11 suites also pass with vanilla rendering.

## Minecraft 26.1–26.2 ports (2026-11-16)

Each of these targets passes 48 unit tests and the complete graphical world suite
with vanilla and with the exact Sodium/Mod Menu versions below. Tests cover geometry
retention across 2/8/2/4, actual block rebuilds, genuine renderer reloads, entity
visibility, Mod Menu edits and walking/block breaking during resource reloads.

| Branch | Fabric API | Sodium | Mod Menu |
| --- | --- | --- | --- |
| 26.1 | 0.145.1+26.1 | 0.8.9+mc26.1.1 | 18.0.2 |
| 26.1.1 | 0.145.4+26.1.1 | 0.8.9+mc26.1.1 | 18.0.2 |
| 26.1.2 | 0.155.3+26.1.2 | 0.9.2+mc26.1.2 | 18.0.2 |
| 26.2 | 0.161.0+26.2 | 0.9.2+mc26.2 | 20.0.3 |

26.1.x uses the older vanilla `LevelRenderer.cullTerrain` cache adapter and
Minecraft input accessors. With Sodium, that vanilla adapter is skipped because
Sodium replaces the method; Sodium's own section manager receives distance updates.
26.1/26.1.1 use Sodium's section map; 26.1.2/26.2 use its newer section storage and
uniform-buffer reservation. 26.2 uses the split `LevelExtractor` renderer API.
All four builds use Java 25, unobfuscated official names and client-only dependencies.
Long-flight memory, shaders and visual behavior with arbitrary resource packs still
require the manual checks documented in each README.

## Adaptive rendering across supported versions

Each branch includes adaptive render distance, entity and particle controls, terrain
filtering, hot/soft sections and the profiler. Renderer hooks follow the Minecraft
and Sodium versions selected by the branch. Vanilla and Sodium have client world
tests on 1.21.5–1.21.11 and 26.x. Versions 1.21.1–1.21.4 have startup smoke tests
and an in-world benchmark command. Use their branch README for the measurement procedure.
