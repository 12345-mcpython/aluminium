# -*- coding: utf-8 -*-
"""Round 581: 21017's reader count is UNKNOWN, not 1 -- and say why the text search failed.

Round 579 searched for the blocker in my own vocabulary and found exactly one place (this cone), which is a count of
registrations, not of readers. Round 580 searched the authoritative texts for the linguistic shape and got 137
clauses over 53 files -- then the SAMPLES refuted the heuristic: most matches are conditions evaluated at the moment
of an event (which the engine already handles), while the problematic case is a standing stat that must lapse, which
is a semantic rather than textual distinction.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CONE = 'src/main/resources/light_cones/21017.json'

ANCHOR = '⚠ 已搜过引擎 ✓：`NUMERIC_VARIABLES` 含 `self_energy_percent` ✓；`MODIFY_ATTR` 的时长只有 `permanent` / `turns` / `until` ✗（⚠ 没有「随条件存在」这一种 ✓）。'
ADD = (' ⚠ **读者数：未知** ✗ —— ⚠ 本日曾试图测它（⚠ 见第 580 轮 ✓）：⚠ 在 `aluminium_texts` 上按**文字形状**搜'
       '（⚠ 「当…时」＋「提高」且邻近无「持续 N 回合」✓）得 **137 条 / 53 个文件** ✗，'
       '⚠ 但**抽样即否掉该启发式** ✗ —— ⚠ 绝大多数命中是**结算那一刻的判据** ✓'
       '（⚠ 例如 1002 的「当受击目标处于减速状态时终结技倍率提高」✓、1008 的「生命值 ≤50% 时战技伤害提高」✓，'
       '⚠ 这些引擎本来就能写 ✓），⚠ 而真正有问题的是「**必须随条件消失而撤销的常驻属性**」✗ —— '
       '⚠ 那是**语义**差别，不是**文字**差别 ✓ ⇒ ⚠ **无法用文本搜索确立 ≥2 个读者** ✗ ⇒ '
       '⚠ 按纪律（**不造没有读者的能力** ✓）**不建** ✓，⚠ 并如实把读者数记为**未知** ✗，'
       '⚠ **不记为 1** ✗（⚠ 只搜自己的词汇只得 1 处 ✓ —— ⚠ 那说明的是「已登记处数」 ✗，不是读者数 ✓）。')

path = WORK + '/' + CONE
orig = io.open(path, encoding='utf-8').read()
n = orig.count(ANCHOR)
print('anchor count: %d' % n)
if n == 0:
    print('REFUSING: anchor not found')
    sys.exit(1)
if '"' in ADD or "'" in ADD:
    print('REFUSING: the addition carries a quote')
    sys.exit(1)
patched = orig.replace(ANCHOR, ANCHOR + ADD)
json.loads(patched)
io.open(path, 'w', encoding='utf-8', newline='').write(patched)
print('21017 note updated in all %d occurrences, and it still parses as JSON' % n)


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
                      'docs: 21017 reader count is unknown, not one -- the text heuristic does not discriminate'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
