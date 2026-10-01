# -*- coding: utf-8 -*-
"""Round 583: 21038's reason blamed a missing "3-turn cooldown" -- cooldown has existed all along.

Evidence: TriggerSpec.java:84-85 declares `cooldown`, and shipped content uses it (character 1002 cooldown 2, 1101
cooldown 1, 1203 cooldown 2, 1207 cooldown 1, 1223 cooldown 1, and the light cone 22003 cooldown 1 -- a cone of the
same kind). The remaining blockers are the other two: cumulative HP loss within one hit, and HP spending.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
REG = 'src/main/resources/light_cones/_unmodelled.json'

OLD = 'Needs cumulative HP loss within one hit, HP spending, and a 3-turn cooldown. Reader count: 1.'
NEW = ('⚠ RE-VERIFIED 2026-09-30: the third item used to be stated as a missing 3-turn cooldown, and THAT PART WAS '
       'WRONG -- `TriggerSpec` declares `cooldown` (TriggerSpec.java:84-85) and shipped content already uses it '
       '(character 1002 cooldown 2, 1101 cooldown 1, 1203 cooldown 2, 1207 cooldown 1, 1223 cooldown 1, and the '
       'light cone 22003 cooldown 1 -- a cone of this very kind), so 「每 3 回合只能触发 1 次」 is expressible. '
       'What genuinely remains is the other two: a cumulative HP loss WITHIN ONE HIT, and a notion of SPENDING '
       'one own HP -- the engine has the HP_LOST and TAKING_HIT events and a `self hp_percent` variable, but no '
       'accumulator for one hit and no HP-spending event. Reader count for those two: 1 (this cone).')

path = WORK + '/' + REG
orig = io.open(path, encoding='utf-8').read()
n = orig.count(OLD)
print('anchor count: %d' % n)
if n != 1:
    print('REFUSING: anchor not unique')
    sys.exit(1)
if '"' in NEW or "'" in NEW:
    print('REFUSING: the replacement carries a quote')
    sys.exit(1)
patched = orig.replace(OLD, NEW, 1)
json.loads(patched)
io.open(path, 'w', encoding='utf-8', newline='').write(patched)
print('21038 reason corrected, and the file still parses as JSON')


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
                      'docs: 21038 reason -- the cooldown it named as missing has existed all along'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
