# -*- coding: utf-8 -*-
"""Round 611: 1104's technique item is stale too -- the gate it says is missing is in the same file.

technique_opening_shield (line 172) uses `self has_state 秘技`, SHIELD with scale owner_def, percent 0.24,
amount 150, turns 2, target all_allies, and its own note says the clause was registered from round 141 and
reclaimed in round 178. Two short fragments, each required to be unique.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1104.json'

PAIRS = [
    ('缺**「用过秘技」**这个门槛',
     '✅ **该门槛已经具备** ✓（见本档 `technique_opening_shield` ✓：`self has_state 秘技` ✓ ＋ `SHIELD` '
     '`scale owner_def` / `percent 0.24` / `amount 150` / `turns 2` ✓ ＋ `target: all_allies` ✓）—— '
     '**2026-09-30 修订** ✓。⚠ 以下是**修订前**的记录，保留以便对照：'),
    ('那是近似，故不写）',
     '那是近似 ✓）⚠ 该项**第 178 轮补上能力后已回收** ✓，⚠ 见 `technique_opening_shield` 的注记 ✓。'),
]

path = WORK + '/' + CHAR
text = io.open(path, encoding='utf-8').read()
for old, new in PAIRS:
    print('anchor %r count: %d' % (old[:22], text.count(old)))
    if text.count(old) != 1:
        print('REFUSING: that anchor is not unique')
        sys.exit(1)
    if '"' in new or "'" in new:
        print('REFUSING: a replacement carries a quote')
        sys.exit(1)
for old, new in PAIRS:
    text = text.replace(old, new, 1)
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('1104 technique item revised, and the file still parses as JSON')


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
    print('NOT auto-rolled-back: revert with git checkout if needed')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'docs: 1104 -- the technique gate it called missing is in the same file'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
