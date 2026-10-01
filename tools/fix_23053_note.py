# -*- coding: utf-8 -*-
"""Round 653: cone 23053's note still calls the per-turn spend counter missing, and the two rules below it ARE that.

The note appears once per superimposition rank -- five copies, the established count for a light cone. The replacement
names the two rules that implement it, so the entry cannot rot the same way twice.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CONE = 'src/main/resources/light_cones/23053.json'

OLD = ('⚠ **同句的【推流】部分已登记** ✗：「若在**同一回合内**消耗大于等于#7[i]个战技点则获得【推流】」'
       '需要**一个每回合重置的计数** ✗（`per_turn` 是"规则级发放上限" ✗，不是计数器重置 ✗）。')
NEW = ('✅ **同句的【推流】部分已写出** ✓ —— **2026-09-30 修订** ✓（⚠ 原写「已登记 ✗ …需要**一个每回合重置的'
       '计数** ✗」✗，⚠ 而本阶**下面那两条规则**正是它 ✓：`cone23053_turn_spend_count` ✓（⚠ 消费事件上 '
       '`ADD_STACK` 记自身计数器 `本回合消耗` ✓）＋ `cone23053_turn_spend_pays_out` ✓（⚠ '
       '`self_stacks:本回合消耗 >= 4` 时结算 ✓））。⭐ 记法值得留档 ✓：⚠ **"本次消耗了多少"这个量不需要引擎知道** '
       '✗ —— ⚠ **让规则自己在消费事件上记一个计数器** ✓，⚠ 再用现成的 `self_stacks:<NAME>` 读回来 ✓ '
       '（⚠ 与 `消耗层数` 那条同法 ✓，⚠ 两条都已是出货能力 ✓）。')

path = WORK + '/' + CONE
text = io.open(path, encoding='utf-8').read()
count = text.count(OLD)
print('stale-note anchor count: %d' % count)
if count != 5:
    print('REFUSING: a light cone carries one note per rank, so this must be 5')
    sys.exit(1)
if '"' in NEW:
    print('REFUSING: the replacement carries a straight quote')
    sys.exit(1)
patched = text.replace(OLD, NEW)
json.loads(patched)
io.open(path, 'w', encoding='utf-8', newline='').write(patched)
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
