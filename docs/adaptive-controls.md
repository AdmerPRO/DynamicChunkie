# Adaptive controls (0.1.0)

All options are available in Mod Menu and `config/dynamicchunkie.json`. The example
configuration includes the defaults. Simulation Distance remains untouched.

## Visibility and prediction

Horizontal rays are weighted into front/back/left/right measurements using player yaw;
an additional ray follows actual gaze. Forward visibility can modestly raise openness
when looking out across a valley with a wall behind the player. Existing horizontal,
vertical, sky and altitude gates still apply.

During fast Elytra flight, velocity and gaze define a probe up to 24 blocks ahead.
Only a loaded, unobstructed route permits three preview rays. Their openness adds at
most 0.12 to the score, gated by movement visibility and horizontal openness. This is
an early target adjustment through Minecraft's normal RD option, not custom loading.

Analysis defaults to 1500 ms stationary, 750 ms walking/turning and 250 ms in Elytra
flight. CPU pressure can double the interval. Disabling adaptive analysis uses the
original fixed interval. Stationary results use quarter-block positions, five-degree
angles and a world revision; block/chunk updates invalidate them. Entries expire after
3000 ms. The bounded result cache evicts older/farther entries preferentially.

## Performance and memory

A reused 120-sample ring measures actual frame cadence and render-thread CPU time.
Mean/p95 frame times are evaluated once per second after warmup. Sustained overload
reduces the distance ceiling; ten seconds of headroom restores it gradually, never
above the environment target. Stable VSync/FPS caps with low CPU work do not count
as overload. The game's GPU timing is used only when available; absent GPU timing
is unknown, never a made-up utilization reading. This is a heuristic, not hardware
profiling or an OS GPU/CPU load monitor. Menus and pauses are excluded.

`cacheMemoryLimitMb` is a **soft native section-slot reservation budget**, using a
conservative 1 KiB accounting charge per slot, and a bound on the mod's own result
cache (also conservatively charged 1 KiB/entry). It is not a hard cap on compiled
geometry, native allocations or VRAM. Active sections can exceed the reservation
budget. Minecraft/Sodium own geometry, uploading and unloading; nearby compiled
meshes are retained by the existing cache integration. The mod does not arbitrarily
free engine GPU buffers. Actual low JVM heap headroom clears cached analyses and
lowers the target. No mod can guarantee total process RAM with this accounting.
Entity-analysis state is pruned to its current bounded candidate set.

## Distance transitions

Normal growth defaults to +2 chunks per second; reductions apply directly after
hysteresis. Longer transition intervals may make growth slower. Teleports (48 blocks
by default, with a velocity allowance) and dimension changes start at RD 2, clamped
to the configured minimum, then grow +1 per second. Attacks and being hurt bypass
this reset for five seconds. Combat during recovery cancels the slow ramp and
restores the accepted target. A pause never accumulates a burst of pending steps.

## Transparent loading

`simpleLoadingScreens` hides the world-loading background and the red/logo resource
reload presentation. Only a translucent progress bar remains at the native position.
Indeterminate world loading uses an animated bar until progress is known.

`loadingInputPassthrough` allows keyboard/mouse gameplay during resource reloads when
an existing player and its chunk are available. The native reload object remains
installed: rendering, completion callbacks, resource errors and fade-out still run.
Only input-time overlay lookups are bypassed. Normal menus are never dismissed.
The initial world join still waits for Minecraft's native readiness/handshake before
allowing movement; there is no usable world to control earlier. Resource rebuilding
can still cause engine stalls; this feature cannot promise uninterrupted frame times.

## Validation

Unit tests cover exact DDA, policy gates, bounded cache eviction/expiry, performance
hysteresis, memory accounting and teleport/combat rates. The graphical reload regression
holds an unfinished native reload, moves the player, breaks a block, and then verifies
normal completion. Renderer regressions verify near-mesh identity and block updates.

Manual checks: reload an actual resource pack (including one with errors), move, look,
attack, place/break blocks, use items and finish the reload. Verify a translucent bar
without red/logo, normal menus, and initial-world loading. Test teleports with and
without recent combat, normal +2/s and teleport +1/s ramps. Fly toward open terrain
and through enclosed terrain. Under real GPU/CPU load inspect debug frame times and
analysis intervals; verify FPS caps alone do not repeatedly shrink RD. Change blocks
while stationary and confirm cached openness refreshes. Check minimum/maximum,
restore on disable, and unchanged Simulation Distance on a remote server too.

On master (Minecraft 26.3), the full build and 48 unit tests passed. Client-world
regressions passed with vanilla and Sodium `0.9.3-beta.1+mc26.3`, including walking
and breaking a block during an unfinished reload and exactly one completion callback.
