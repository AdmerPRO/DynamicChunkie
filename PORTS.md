# Branches and validation

Version **0.1.0** on every branch. Author: **AdmerPRO**.
License: **GPL-3.0-only**. No Simulation Distance/server simulation changes.

| Branch | Minecraft | Java |
| --- | --- | --- |
| master | 26.3 | 25 |
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
