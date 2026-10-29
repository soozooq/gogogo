#!/usr/bin/env python3
"""Inspect licenses declared in POMs for the resolved host runtime graph.

This does not audit every source file or certify a distribution. Native vendored
notices and source/relinking obligations are documented separately.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--graph', required=True)
parser.add_argument('--gradle-cache', required=True)
parser.add_argument('--output', required=True)
args = parser.parse_args()
cache = Path(args.gradle_cache) / 'caches/modules-2/files-2.1'
namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}

def declarations(group, name, version, seen=()):
    coordinate = (group, name, version)
    if coordinate in seen or len(seen) > 12 or not all(re.fullmatch(r'[A-Za-z0-9_.-]+', x) for x in coordinate):
        return []
    results = []
    for pom in (cache / group / name / version).glob('*/*.pom'):
        root = ET.parse(pom).getroot()
        licenses = root.findall('m:licenses/m:license', namespace)
        for license in licenses:
            results.append({
                'name': license.findtext('m:name', default='', namespaces=namespace),
                'url': license.findtext('m:url', default='', namespaces=namespace),
                'declared_by': ':'.join(coordinate),
                'pom_sha256': hashlib.sha256(pom.read_bytes()).hexdigest(),
            })
        if not licenses:
            parent = root.find('m:parent', namespace)
            if parent is not None:
                values = [parent.findtext('m:' + key, default='', namespaces=namespace)
                          for key in ('groupId', 'artifactId', 'version')]
                results.extend(declarations(*values, seen=(*seen, coordinate)))
    return results

rows = []
for component in json.loads(Path(args.graph).read_text()):
    licenses = [] if component['source_kind'] == 'local_project' else declarations(
        component['group'], component['name'], component['version'])
    rows.append(dict(component, declared_licenses=licenses))
report = {
    'scope': 'RESOLVED_HOST_RUNTIME_POM_DECLARATIONS_NOT_FULL_SOURCE_AUDIT',
    'components': rows,
    'unresolved': [':'.join(x[k] for k in ('group', 'name', 'version'))
                   for x in rows if x['source_kind'] != 'local_project' and not x['declared_licenses']],
}
Path(args.output).write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({'components': len(rows), 'unresolved': report['unresolved']}))
