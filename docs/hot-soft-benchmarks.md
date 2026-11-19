# Hot/soft automated benchmarks — Minecraft 26.3

Use JDK 25 and a graphical session. Tests create and delete a disposable flat
world; they do not open your saves. Benchmark fixtures belong to the separate
`gametest` source set and are not distributed in the mod JAR.

```sh
./gradlew runClientGameTest -Pbenchmark
./gradlew runClientGameTest -Pbenchmark -PwithSodium
python3 scripts/summarize-benchmark.py build/benchmarks/*.json
```

The default is 100 warmup ticks, 200 measured ticks and two repetitions of each
scene. At normal tick speed these are roughly five and ten seconds per phase.
OFF/ON order alternates AB then BA. Only hot/soft changes between phases: RD is
fixed at eight, other adaptive visual controls are off, VSync is off and the
framerate option is set to 260 (the vanilla unlimited option). Simulation
Distance is asserted unchanged and the original mod/video options are restored.
Do not run two graphical suites simultaneously or change window focus/settings
mid-run. Close other workloads and repeat the full process to assess variance.

For a quick harness check, not a performance conclusion:

```sh
./gradlew runClientGameTest -Pbenchmark -PbenchmarkWarmupTicks=20 -PbenchmarkSampleTicks=40 -PbenchmarkRepeats=1
```

For longer sampling, override those three properties, for example 200/600/4.
Samples stop after the requested game ticks; the report records real wall time.
Fabric's client-test scheduler synchronizes game threads, so this is a controlled
integration workload, not an unmodified gameplay FPS benchmark. Validate results
in your actual modpack and world before extrapolating.

Scenes:

- `top_down_flight`: controlled creative flight camera looking straight down and
  moving along a repeated path. It isolates camera geometry; it does not simulate
  real Elytra physics, network travel or predictive chunk streaming.
- `horizon`: stationary camera facing open terrain and synthetic structures.
- `cave`: camera under a small roof, with nearby and distant geometry.
- `offscreen_updates`: first build geometry west of the camera, turn east, then
  alternate a block in the western section. Measures safe rebuild deferral.

Reports in `build/benchmarks/`:

- JSON: configuration, renderer, wall time, frames, frame interval mean/p50/p95/p99,
  native geometry candidates, extra skipped sections, hot activity, scan time,
  deferrals/replays, fallback count, charged metadata estimate, JVM heap and GC.
- CSV: raw per-frame intervals, tagged by scene, repetition and OFF/ON state.
- The summary script reports paired FPS changes and p95 frame times; a positive
  FPS change is an observed result for that run, not a guaranteed improvement.

Intervals use `System.nanoTime` at completed Minecraft frame boundaries. The
recorder allocates its bounded 65536-entry buffer once per phase; no frame writes
files or samples heap/GC. Longer runs retain the first 65536 intervals for
percentiles and CSV, while the FPS mean includes all intervals; JSON flags this.
A phase with no frames or no active treatment frames fails. Logical soft counts
include air/unloaded coordinates and are calculated after recording stops.

Both phases collect the same candidate telemetry while recording. The hot/soft
metadata charge is a conservative bookkeeping estimate, not a JVM allocation
measurement. The JSON heap snapshots include the integrated server and GC; their
difference is **not** RAM saved by the renderer. GPU VRAM is not measured. This
implementation retains cold meshes and chunk data, with zero explicit geometry
or chunk-data evictions. Extra scan/filter and command-cache invalidation can
cost more than they save. Do not quote a universal FPS or memory percentage.

Functional regressions run separately:

```sh
./gradlew build runClientGameTest
./gradlew runClientGameTest -PwithSodium
```

They cover all 26 soft neighbours, coordinate packing, real camera membership,
cold rebuild admission, urgent and initial mesh safety, replay on returning to
view, disabling, and Simulation Distance isolation, alongside existing tests.
Benchmark runs deliberately select only the benchmark entrypoint; a normal run
regenerates the normal test metadata.
