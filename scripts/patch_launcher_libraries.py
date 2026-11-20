#!/usr/bin/env python3
"""Generate patched Minecraft 26.3 base-version metadata without modifying the input or launcher."""
import argparse
import copy
import json
from pathlib import Path
import re
import sys
import urllib.error
import urllib.request

CENTRAL = 'https://repo.maven.apache.org/maven2/'


def load_versions(path):
    versions = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith('#'):
            key, value = line.split('=', 1)
            versions[key.strip()] = value.strip()
    for key in ('lz4', 'guava', 'netty', 'log4j'):
        if not re.fullmatch(r'[0-9][A-Za-z0-9_.-]*', versions.get(key, '')):
            raise ValueError('Invalid or missing patch version: ' + key)
    return versions


def family(group, artifact):
    if (group, artifact) == ('at.yawk.lz4', 'lz4-java'):
        return 'lz4'
    if (group, artifact) == ('com.google.guava', 'guava'):
        return 'guava'
    if group == 'io.netty' and artifact.startswith('netty-') and not artifact.startswith('netty-tcnative'):
        return 'netty'
    if group == 'org.apache.logging.log4j':
        return 'log4j'
    return None


def artifact_metadata(path):
    url = CENTRAL + path
    with urllib.request.urlopen(url + '.sha1', timeout=30) as response:
        sha1 = response.read(256).decode('ascii').strip()
    if not re.fullmatch(r'[a-fA-F0-9]{40}', sha1):
        raise ValueError('Invalid Maven checksum for ' + path)
    with urllib.request.urlopen(urllib.request.Request(url, method='HEAD'), timeout=30) as response:
        size = int(response.headers['Content-Length'])
    if size <= 0:
        raise ValueError('Invalid Maven artifact size for ' + path)
    return {'path': path, 'url': url, 'sha1': sha1.lower(), 'size': size}


def patch_document(document, versions, resolve=artifact_metadata):
    if document.get('id') != '26.3' or document.get('inheritsFrom'):
        raise ValueError('Input must be complete Minecraft 26.3 base metadata, not an inherited Fabric profile')
    result = copy.deepcopy(document)
    found = set()
    changed = []
    for library in result['libraries']:
        coordinate = library['name'].split(':')
        if len(coordinate) not in (3, 4):
            raise ValueError('Unsupported Maven coordinate: ' + library['name'])
        group, artifact, old_version = coordinate[:3]
        key = family(group, artifact)
        if key is None:
            continue
        found.add(key)
        for segment in (artifact, *coordinate[3:]):
            if not re.fullmatch(r'[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*', segment):
                raise ValueError('Unsafe Maven artifact or classifier: ' + segment)
        version = versions[key]
        classifier = '-' + coordinate[3] if len(coordinate) == 4 else ''
        path = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}{classifier}.jar"
        if 'natives' in library or 'classifiers' in library.get('downloads', {}):
            raise ValueError('Legacy native-download format requires explicit support: ' + library['name'])
        library['name'] = ':'.join([group, artifact, version, *coordinate[3:]])
        library['downloads'] = {'artifact': resolve(path)}
        library.pop('url', None)
        changed.append({'module': group + ':' + artifact, 'from': old_version, 'to': version, 'classifier': classifier})
    if found != {'lz4', 'guava', 'netty', 'log4j'}:
        raise ValueError('Incomplete Minecraft library list; missing: ' + ', '.join(sorted({'lz4', 'guava', 'netty', 'log4j'} - found)))
    return result, changed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, type=Path, help='Complete Minecraft 26.3 version JSON')
    parser.add_argument('--output', type=Path, default=Path('tmp/launcher/26.3.json'))
    parser.add_argument('--versions', type=Path, default=Path('config/security-versions.properties'))
    args = parser.parse_args()
    try:
        if args.input.resolve() == args.output.resolve():
            raise ValueError('Choose a separate output file; the input is never overwritten')
        document, changes = patch_document(json.loads(args.input.read_text()), load_versions(args.versions))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(document, indent=2) + '\n')
        print(f'Generated {args.output} with {len(changes)} patched library entries, including native classifiers.')
        print('No installed launcher files were changed. Apply the library overrides to the matching instance before launching.')
        return 0
    except (OSError, ValueError, KeyError, urllib.error.URLError) as error:
        print(f'Launcher metadata generation failed: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
