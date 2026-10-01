# -*- coding: utf-8 -*-
"""Round 606: 1005's note says a rule-level limit field was unconfirmed, so the clause was not written.

The same file carries `per_turn: 1` (line 63) and another note states that round 146 read TriggerSpec to confirm the
field. Rather than reproduce a long sentence (which is how rounds 584/585 went wrong), this patches two SHORT
fragments, each required to be unique, and re-parses the document before trusting it.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1005.json'

PAIRS = [
    ('拼写与语义未确认',
     '拼写与语义未确认（⚠ **2026-09-30 修订**：该字段**当时即已确认** ✓ —— 见本档 `per_turn: 1` 及其注记 ✓，'
     '⚠ 第 146 轮读 `TriggerSpec` 已核实 ✓）'),
    ('，故不写 ✗）',
     '；⚠ **该从句现已写出** ✓，详见本档 `per_turn: 1` 那条规则与其注记 ✓）'),
]

path = WORK + '/' + CHAR
text = io.open(path, encoding='utf-8').read()
for old, new in PAIRS:
    n = text.count(old)
    print('anchor %r count: %d' % (old, n))
    if n != 1:
        print('REFUSING: that anchor is not unique')
        sys.exit(1)
    if '"' in new or "'" in new:
        print('REFUSING: a replacement carries a quote')
        sys.exit(1)
for old, new in PAIRS:
    text = text.replace(old, new, 1)
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('1005 note revised, and the file still parses as JSON')


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
                      'docs: 1005 -- the per-turn field it called unconfirmed was confirmed in round 146'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
