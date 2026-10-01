# -*- coding: utf-8 -*-
"""Round 469: a machine check for the fixture-shadowing hazard (src/test/resources vs shipped content)."""

import glob
import io
import json
import os
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'

# ids actually shipped (a content file under src/main/resources)
shipped = {}
for kind in ('characters', 'light_cones', 'relic_sets'):
    for p in glob.glob(WORK + '/src/main/resources/%s/*.json' % kind):
        name = os.path.basename(p)[:-5]
        if name.startswith('_'):
            continue
        shipped.setdefault(kind, set()).add(name)

# ids occupied by a test fixture
fixtures = {}
for p in glob.glob(WORK + '/src/test/resources/**/*.json', recursive=True):
    rel = os.path.relpath(p, WORK + '/src/test/resources').replace('\\', '/')
    parts = rel.split('/')
    if len(parts) != 2:
        continue
    kind, name = parts[0], parts[1][:-5]
    fixtures.setdefault(kind, set()).add(name)

print('shipped: %s' % {k: len(v) for k, v in sorted(shipped.items())})
print('fixtures: %s' % {k: sorted(v) for k, v in sorted(fixtures.items())})

bad = []
for kind, names in fixtures.items():
    for name in sorted(names):
        if name in shipped.get(kind, set()):
            # a fixture on an id that HAS shipped content shadows it on the test classpath -- and a fixture on a
            # relic set that has a RULE FILE is the specific configuration SummonOpTest warns about.
            has_rules = os.path.exists(WORK + '/src/main/resources/relic_sets/%s.json' % name) and kind == 'relic_sets'
            bad.append((kind, name, has_rules))

for kind, name, has_rules in bad:
    print('COLLISION %s/%s  (shipped content is shadowed on the test classpath%s)'
          % (kind, name, '; this set HAS a rule file -- the configuration that hid set 108 4-piece' if has_rules else ''))
if bad:
    print('SHADOWING: %d fixture(s) collide with shipped ids' % len(bad))
    if any(h for _, _, h in bad):
        print('FAIL: at least one fixture sits on an id with a rule file')
        sys.exit(1)
    print('OK (collisions exist but none is on a rule-file set -- intended, and it must be moved before reclaiming)')
else:
    print('OK: no fixture shadows shipped content')
