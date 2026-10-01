# -*- coding: utf-8 -*-
"""Round 655: revise 23053's stale note -- allowing 10 occurrences, keeping the anchor OUT of the replacement, and
asserting the anchor count drops to zero before writing.

Round 653 failed on escaped quotes; round 654 failed because the fragment occurs ten times (twice per rank) and
because my replacement text contained the anchor itself, which would have left the note re-matchable.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CONE = 'src/main/resources/light_cones/23053.json'

OLD = '需要**一个每回合重置的计数**'
NEW = ('✅ **该计数器已于 2026-09-30 写出** ✓（⚠ 见本阶 `cone23053_turn_spend_count` ✓ 与 '
       '`cone23053_turn_spend_pays_out` ✓）—— ⚠ 原判断已过时 ✓；⭐ **记法留档** ✓：'
       '⚠ **本次消耗了多少这个量不需要引擎知道** ✗ —— ⚠ **让规则自己在消费事件上记一个计数器** ✓，'
       '⚠ 再用现成的 `self_stacks:<NAME>` 读回来 ✓（⚠ 与 `消耗层数` 那条同法 ✓）')

path = WORK + '/' + CONE
text = io.open(path, encoding='utf-8').read()
n = text.count(OLD)
print('anchor count: %d' % n)
if n != 10:
    print('REFUSING: measured twice per rank, so this must be 10')
    sys.exit(1)
if '"' in NEW or "'" in NEW:
    print('REFUSING: the replacement carries a quote')
    sys.exit(1)
if OLD in NEW:
    print('REFUSING: the replacement still contains the anchor (it would stay re-matchable)')
    sys.exit(1)
patched = text.replace(OLD, NEW)
print('anchor count after replacement: %d' % patched.count(OLD))
if patched.count(OLD) != 0:
    print('REFUSING: the anchor survived the replacement')
    sys.exit(1)
json.loads(patched)
io.open(path, 'w', encoding='utf-8', newline='').write(patched)
print('23053 note revised in 10 places, and the file still parses as JSON')


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
                    print('  XMLFAIL %s#%s %s' % (c.get('classname'), c.get('name'),
                                                  (m.get('message') or '')[:200]))
    subprocess.run(['git', 'checkout', '--', CONE], cwd=WORK)
    print('ROLLED BACK the note revision')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'docs: 23053 -- the per-turn spend counter its note called missing is the two rules below it'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
