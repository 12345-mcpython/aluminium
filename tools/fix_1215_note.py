# -*- coding: utf-8 -*-
"""Round 584: 1215's eidolon-2 note said there is no way to say energy became full. That stopped being true.

ENERGY_GAINED plus the self_energy_percent variable say exactly that, and character 1310's talent ships such a rule
(rounds 562-567). The second half of the note still holds: the reset is a per-unit trigger count.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1215.json'

OLD = ', and the reset is a per-unit trigger count).'
NEW = (', and the reset is a per-unit trigger count). '
       '⚠ RE-VERIFIED 2026-09-30: the FIRST half of that was WRONG -- there is now a way to say that energy became '
       'full: the ENERGY_GAINED event together with the self_energy_percent variable, both of which exist, and the '
       'talent of character 1310 ships exactly that rule. ⚠ What genuinely remains is the SECOND half: a counter '
       'keyed by WHO triggered it, which no field provides (per_turn and cooldown both count the rule OWNER turns).')

path = WORK + '/' + CHAR
orig = io.open(path, encoding='utf-8').read()
n = orig.count(OLD)
print('anchor count: %d' % n)
if n == 0:
    print('REFUSING: anchor not found')
    sys.exit(1)
if '"' in NEW or "'" in NEW:
    print('REFUSING: the replacement carries a quote')
    sys.exit(1)
patched = orig.replace(OLD, NEW)
json.loads(patched)
io.open(path, 'w', encoding='utf-8', newline='').write(patched)
print('1215 note corrected in all %d occurrences, and it still parses as JSON' % n)


def run(*a, q=True):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + (['--quiet'] if q else []) + ['--console=plain'],
                          cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')


suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    import glob as g
    import xml.etree.ElementTree as E
    for p in g.glob(WORK + '/build/test-results/test/*.xml'):
        try:
            r = E.parse(p).getroot()
        except Exception:
            continue
        for c in r.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    print('  XMLFAIL ' + (m.get('message') or '')[:300])
    io.open(path, 'w', encoding='utf-8', newline='').write(orig)
    print('rolled back')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'docs: 1215 eidolon 2 -- energy-became-full is expressible now; the per-unit counter is not'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
