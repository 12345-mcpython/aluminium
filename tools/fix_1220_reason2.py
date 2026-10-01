# -*- coding: utf-8 -*-
"""Round 572: correct 1220 reason 1, this time with NO straight double quotes in the JSON string.

Round 570 failed because the replacement text carried unescaped `"` inside a JSON string value, which made
characters/1220.json malformed (Gson: Unterminated object at path $[0].note). This version uses 「」 only.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1220.json'

OLD = '① 「若不存在可攻击的主目标，则攻击敌方**随机单体**」✗（**随机目标** ✗）'
NEW = ('① 「若不存在可攻击的主目标，则攻击敌方**随机单体**」✗ —— '
       '⚠ **登记理由已于 2026-09-30 修正** ✓：原写作「缺**随机目标**」✗，**是错的** ✓ —— '
       '`TriggerInterpreter.TARGET_SELECTORS` **早已含 `random_enemy`** ✓（⚠ 已逐项搜过 ✓，'
       '同一次搜索还查出 `random_hit_enemy` ✓，并已在光锥 `21029` 上出货 ✓）。'
       '⭐ **真正的阻碍是它前面那个兜底从句** ✗：「**若不存在可攻击的主目标**」✗ ⇒ '
       '⚠ 引擎没有「**当前没有合法主目标**」这一判据 ✗ ⇒ ⚠ 这才是本条需要的能力 ✓。')

path = WORK + '/' + CHAR
orig = io.open(path, encoding='utf-8').read()
n = orig.count(OLD)
print('anchor count: %d' % n)
if n != 1:
    print('REFUSING: the anchor is not unique')
    sys.exit(1)
patched = orig.replace(OLD, NEW, 1)
if '"' in NEW:
    print('REFUSING: the replacement must not carry a straight double quote')
    sys.exit(1)
io.open(path, 'w', encoding='utf-8', newline='').write(patched)
import json
json.loads(patched)
print('1220 reason 1 corrected, and the file still parses as JSON')


def run(*a, q=True):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + (['--quiet'] if q else []) + ['--console=plain'],
                          cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')


suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    for l in ((suite.stdout or '') + (suite.stderr or '')).strip().split('\n')[-8:]:
        print('  RAW ' + l.strip()[:170])
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
                      'docs: 1220 reason 1 was wrong -- random_enemy exists; the fallback clause is the blocker'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
