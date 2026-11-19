#!/usr/bin/env python3
"""Summarize paired hot/soft A/B reports. Uses only the Python standard library."""
import argparse
import json
from collections import defaultdict
from statistics import mean
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('reports', nargs='+', type=Path)
args = parser.parse_args()
for path in args.reports:
    report = json.loads(path.read_text())
    print(f"\n{path.name}: {report['renderer']}, Minecraft {report['minecraft']}, RD {report['renderDistance']}")
    print(f"Warmup {report['warmupTicks']} ticks; samples {report['sampleTicks']} ticks; repeats {report['repeats']}.")
    if report['warmupTicks'] < 100 or report['sampleTicks'] < 200 or report['repeats'] < 2:
        print('SMOKE TEST: these short samples cannot establish a performance gain.')
    if not report.get('equalCandidateTelemetry', False):
        print('Legacy report: unequal candidate telemetry; rerun before comparing FPS.')
    if not report.get('throttleGuard', False):
        print('Legacy report: no AFK/throttle guard; rerun before drawing performance conclusions.')
    grouped = defaultdict(dict)
    for phase in report['phases']:
        grouped[(phase['scene'], phase['repeat'])][phase['hotSoft']] = phase['measurement']
    scenes = defaultdict(list)
    for (scene, repeat), pair in grouped.items():
        if set(pair) != {False, True}:
            raise SystemExit(f'Missing A/B pair: {scene} repeat {repeat}')
        a, b = pair[False], pair[True]
        if b['activeFrames'] == 0 or a['frames'] == 0 or b['frames'] == 0:
            raise SystemExit(f'Invalid inactive/empty sample: {scene} repeat {repeat}')
        if b['activeFrames'] / b['frames'] < 0.95:
            print(f'Warning: {scene} repeat {repeat} used native fallback for more than 5% of frames.')
        scenes[scene].append((a, b))
    unstable_scenes = []
    print()
    print('| Scene | FPS off / on | Paired FPS change | p95 ms off / on | Extra skipped on | Deferrals on | Active on |')
    print('|---|---:|---:|---:|---:|---:|---:|')
    for scene, pairs in scenes.items():
        avg = lambda side, key: mean(pair[side][key] for pair in pairs)
        delta = mean((b['fps'] / a['fps'] - 1) * 100 for a, b in pairs)
        unstable = any(max(pair[side]['fps'] for pair in pairs) / min(pair[side]['fps'] for pair in pairs) > 2 for side in (0, 1))
        change = 'UNSTABLE' if unstable else f'{delta:+.1f}%'
        if unstable:
            unstable_scenes.append(scene)
        active = mean(b['activeFrames'] / b['frames'] * 100 for _, b in pairs)
        print(f"| {scene} | {avg(0,'fps'):.1f} / {avg(1,'fps'):.1f} | {change} | {avg(0,'p95Ms'):.2f} / {avg(1,'p95Ms'):.2f} | {avg(1,'meanSkipped'):.1f} | {avg(1,'deferrals'):.0f} | {active:.1f}% |")
    print()
    if unstable_scenes:
        print('UNSTABLE: more than 2x variation between repetitions; no gain is reported for ' + ', '.join(unstable_scenes) + '.')
    on = [phase['measurement'] for phase in report['phases'] if phase['hotSoft']]
    print(f"Peak reported hot/soft metadata charge: {max(p['ownCacheChargeBytes'] for p in on)/1024:.1f} KiB (estimate, not a heap allocation measurement).")
    print('Cold meshes and chunks retained; no geometry/chunk evictions. VRAM unmeasured. Heap snapshots include server and GC; do not infer RAM savings.')
