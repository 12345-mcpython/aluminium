# -*- coding: utf-8 -*-
"""Round 574: correct 1221 reason 2 -- the ninth refuted registration, and the same error as 1220 reason 1.

Its note blamed "随机目标" for 「若不存在可反击的目标，则反击敌方随机目标」. random_enemy has existed all along;
the real blocker is the fallback clause. Same recipe as round 572: no straight double quotes, and re-parse the
patched file as JSON before trusting it.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1221.json'

OLD = '② 「若不存在可反击的目标，则反击**敌方随机目标**」✗（**随机目标** ✗）'
NEW = ('② 「若不存在可反击的目标，则反击**敌方随机目标**」✗ —— '
       '⚠ **登记理由已于 2026-09-30 修正** ✓：原写作「缺**随机目标**」✗，**是错的** ✓ '
       '（⚠ 与 1220 ① **同一个错** ✓）—— `TriggerInterpreter.TARGET_SELECTORS` **早已含 `random_enemy`** ✓'
       '（⚠ 已逐项搜过 ✓）。⭐ **真正的阻碍是它前面那个兜底从句** ✗：「**若不存在可反击的目标**」✗ ⇒ '
       '⚠ 引擎缺「**首选目标取不到时退而求其次**」这一判据 ✗ ⇒ ⚠ 该能力现有 **2 个读者** ✓'
       '（⚠ 本条 ＋ 1220 ① ✓；⚠ 另 23050 属同模式的**另一类**（队友排行）✗，不计入 ✓）。')

path = WORK + '/' + CHAR
orig = io.open(path, encoding='utf-8').read()
n = orig.count(OLD)
print('anchor count: %d' % n)
if n != 1:
    print('REFUSING: the anchor is not unique')
    sys.exit(1)
if '"' in NEW or "'" in NEW:
    print('REFUSING: the replacement must carry neither a double nor a single quote')
    sys.exit(1)
patched = orig.replace(OLD, NEW, 1)
json.loads(patched)
io.open(path, 'w', encoding='utf-8', newline='').write(patched)
print('1221 reason 2 corrected, and the file still parses as JSON')


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
                      'docs: 1221 reason 2 -- random_enemy exists; the fallback clause is the blocker'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
