# -*- coding: utf-8 -*-
"""Round 654: revise cone 23053's stale note using quote-free anchors (round 653 failed on escaped quotes).

Two fragments, five occurrences each (a light cone carries one note per rank). Both replacement texts are quote-free
too, since the file is JSON.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CONE = 'src/main/resources/light_cones/23053.json'

PAIRS = [
    ('需要**一个每回合重置的计数**',
     '✅ **该计数器已于 2026-09-30 写出** ✓（⚠ 见本阶 `cone23053_turn_spend_count` ✓ 与 '
     '`cone23053_turn_spend_pays_out` ✓）—— ⚠ 原写「需要**一个每回合重置的计数**」✗ 已过时 ✓；'
     '⭐ **记法留档** ✓：⚠ **本次消耗了多少这个量不需要引擎知道** ✗ —— ⚠ **让规则自己在消费事件上记一个计数器** ✓，'
     '⚠ 再用现成的 `self_stacks:<NAME>` 读回来 ✓（⚠ 与 `消耗层数` 那条同法 ✓）'),
    ('不是计数器重置',
     '不是计数器重置 ✓（⭐ 而**本阶下面那两条规则**正是那个每回合重置的计数器 ✓ —— '
     '2026-09-30 修订 ✓）'),
]

path = WORK + '/' + CONE
text = io.open(path, encoding='utf-8').read()
for old, new in PAIRS:
    n = text.count(old)
    print('anchor %r count: %d' % (old[:18], n))
    if n != 5:
        print('REFUSING: a light cone carries one note per rank, so this must be 5')
        sys.exit(1)
    if '"' in new or "'" in new:
        print('REFUSING: a replacement carries a quote')
        sys.exit(1)
for old, new in PAIRS:
    text = text.replace(old, new)
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('23053 note revised in all 5 ranks, and the file still parses as JSON')


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
