# -*- coding: utf-8 -*-
"""Round 604: 1112's note still opens by saying a clause is unwritten, while the rule above it writes that clause.

characters/1112.json:40-47 is MODIFY_DAMAGE_TAKEN with percent 0.5, damage_type ADDITIONAL, turns 2 and
buff 负债证明 -- exactly the spelling the note's own last sentence describes. Only the opening claim is stale.
The anchor stops before the escaped quotes, so the replacement and the anchor are both quote-free.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1112.json'

OLD = '⚠⚠ **未写「使其受到的追加攻击伤害提高 50%」** ✗ —— **不是近似，是登记** ✓：'
NEW = ('✅ **该从句已经写好** ✓（见上方 `MODIFY_DAMAGE_TAKEN` 规则 ✓：`percent` **0.5** ✓ ＋ '
       '`damage_type` **ADDITIONAL** ✓ ＋ `turns` **2** ✓ ＋ `buff` **负债证明** ✓）—— '
       '**2026-09-30 修订** ✓。⚠ 以下为**修订前**的记录，保留以便对照：')

path = WORK + '/' + CHAR
text = io.open(path, encoding='utf-8').read()
print('anchor count: %d' % text.count(OLD))
if text.count(OLD) != 1:
    print('REFUSING: the anchor is not unique')
    sys.exit(1)
if '"' in NEW or "'" in NEW:
    print('REFUSING: the replacement carries a quote')
    sys.exit(1)
text = text.replace(OLD, NEW, 1)
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('1112 note revised, and the file still parses as JSON')


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
print(subprocess.subprocess if False else subprocess.run(
    ['git', 'commit', '-m',
     'docs: 1112 -- the additional-damage clause its note called unwritten is the rule above it'],
    cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
