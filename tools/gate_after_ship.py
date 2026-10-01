# -*- coding: utf-8 -*-
"""Round 378: the after-shipping gate -- preflight the data, then rebuild the breadth lists."""

import glob
import io
import json
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'

cones = sorted(int(os.path.basename(p)[:-5]) for p in glob.glob(WORK + '/src/main/resources/light_cones/*.json'))
weapons = json.load(io.open(WORK + '/src/main/resources/data/weapons.json', encoding='utf-8'))
weapon_ids = set(int(k) for k in weapons.keys())
missing = [c for c in cones if c not in weapon_ids]
print('PREFLIGHT cones=%d weapon rows=%d missing=%s' % (len(cones), len(weapon_ids), missing))
if missing:
    print('STOP: %d cone(s) have no weapon row, so the engine cannot build them: %s' % (len(missing), missing))
    sys.exit(1)

chars = sorted(int(os.path.basename(p)[:-5]) for p in glob.glob(WORK + '/src/main/resources/characters/*.json'))
relics = sorted(int(os.path.basename(p)[:-5])
                for p in glob.glob(WORK + '/src/main/resources/relic_sets/*.json')
                if not p.endswith('_unmodelled.json'))
print('PREFLIGHT characters=%d relic sets=%d' % (len(chars), len(relics)))

for script in ('.git/breadth_cones.py', '.git/breadth_chars_relics.py'):
    r = subprocess.run([sys.executable, WORK + '/' + script], cwd=WORK, capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    lines = [l.strip() for l in ((r.stdout or '') + (r.stderr or '')).strip().split('\n') if l.strip()]
    verdict = [l for l in lines if 'tree:' in l or 'exit:' in l or 'nothing to commit' in l]
    print('REBUILD %-28s -> %s' % (os.path.basename(script), ' | '.join(v[:90] for v in verdict[-2:]) or 'ok'))


def run(*a, q=True):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + (['--quiet'] if q else []) + ['--console=plain'],
                          cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')


suite = run('test', '--rerun-tasks')
print('suite: %d' % suite.returncode)
if suite.returncode != 0:
    for l in ((suite.stdout or '') + (suite.stderr or '')).strip().split('\n')[-8:]:
        print('  RAW ' + l.strip()[:160])
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
print('branch: ' + subprocess.run(['git', 'status', '-sb'], cwd=WORK, capture_output=True,
                                  text=True).stdout.split('\n')[0])
