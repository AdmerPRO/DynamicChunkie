#!/usr/bin/env python3
"""Query OSV for resolved public Maven coordinates. No source, credentials or classpath paths are sent."""
import argparse
import datetime
import json
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request

API = 'https://api.osv.dev/v1/'


def request_json(endpoint, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    request = urllib.request.Request(API + endpoint, data=data, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def audit(packages):
    findings = []
    for offset in range(0, len(packages), 100):
        batch = packages[offset:offset + 100]
        pending = [({'package': {'ecosystem': 'Maven', 'name': p['name']}, 'version': p['version']}, i)
                   for i, p in enumerate(batch)]
        ids = [set() for _ in batch]
        pages = 0
        while pending:
            pages += 1
            if pages > 100:
                raise ValueError('OSV pagination exceeded safety limit')
            results = request_json('querybatch', {'queries': [q for q, _ in pending]})['results']
            if len(results) != len(pending):
                raise ValueError('OSV returned an incomplete batch')
            more = []
            for (query, index), result in zip(pending, results):
                ids[index].update(v['id'] for v in result.get('vulns', []))
                if result.get('next_page_token'):
                    more.append(({**query, 'page_token': result['next_page_token']}, index))
            pending = more
        findings.extend({**package, 'vulnerabilityIds': sorted(found)} for package, found in zip(batch, ids))
    return findings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inventory', type=Path)
    parser.add_argument('--launcher', action='store_true', help='Read Maven coordinates from a complete launcher version JSON')
    parser.add_argument('--output', type=Path, default=Path('tmp/security/osv.json'))
    args = parser.parse_args()
    try:
        packages = json.loads(args.inventory.read_text())
        if args.launcher:
            coordinates = set()
            for library in packages['libraries']:
                parts = library['name'].split(':')
                if len(parts) not in (3, 4):
                    raise ValueError('Unsupported launcher Maven coordinate')
                coordinates.add((':'.join(parts[:2]), parts[2]))
            packages = [{'name': name, 'version': version} for name, version in sorted(coordinates)]
        if not isinstance(packages, list):
            raise ValueError('Expected a dependency list; use --launcher for version metadata')
        findings = audit(packages)
        identifiers = sorted({v for package in findings for v in package['vulnerabilityIds']})
        details = {v: request_json('vulns/' + urllib.parse.quote(v, safe='')) for v in identifiers}
        report = {'checkedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  'source': API, 'packages': findings, 'advisories': details,
                  'limits': ['No returned advisory is not proof of safety or database coverage.',
                             'Minecraft, Fabric and Modrinth artifacts may not be indexed under these Maven names.',
                             'Build plugins/tool dependencies and code reachability are not assessed.']}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + '\n')
        print(f'Queried {len(findings)} Maven coordinates; {len(identifiers)} advisory IDs. Report: {args.output}')
        return 1 if identifiers else 0
    except (OSError, ValueError, KeyError, urllib.error.URLError) as error:
        print(f'Audit incomplete: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
