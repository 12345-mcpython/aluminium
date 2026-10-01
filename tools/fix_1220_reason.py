# -*- coding: utf-8 -*-
"""Round 570: correct 1220's registered reason 1 -- the seventh refuted registration.

It claimed 「攻击敌方随机单体」 was blocked by "随机目标". `TriggerInterpreter.TARGET_SELECTORS` has held
`random_enemy` all along (read in round 549). The real blocker is the FALLBACK clause that precedes it.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1220.json'

OLD = '① 「若不存在可攻击的主目标，则攻击敌方**随机单体**」✗（**随机目标** ✗）'
NEW = ('① 「若不存在可攻击的主目标，则攻击敌方**随机单体**」✗ —— '
       '⚠ **登记理由已于 2026-09-30 修正** ✓：原写作"缺**随机目标**"✗，**是错的** ✓ —— '
       '`TriggerInterpreter.TARGET_SELECTORS` **早已含 `random_enemy`** ✓（⚠ 已逐项搜过 ✓，'
       '同一次搜索还查出 `random_hit_enemy` ✓）；⭐ **真正的阻碍是它前面那个兜底从句** ✗：'
       '「**若不存在可攻击的主目标**」✗ ⇒ ⚠ 引擎没有"**当前没有合法主目标**"这一判据 ✗ '
       '⇒ ⚠ 这才是本条需要的能力 ✓（⚠ 与本段第七条被推翻的登记同批 ✓）')

path = WORK + '/' + CHAR
orig = io.open(path, encoding='utf-8').read()
n = orig.count(OLD)
print('anchor count: %d' % n)
if n != 1:
    print('REFUSING: the anchor is not unique')
    sys.exit(1)
io.open(path, 'w', encoding='utf-8', newline='').write(orig.replace(OLD, NEW, 1))
print('1220 reason 1 corrected')


def run(*a, q=True):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + (['--quiet'] if q else []) + ['--console=plain'],
                          cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')


suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    for l in ((suite.stdout or '') + (suite.stderr or '')).strip().split('\n')[-12:]:
        print('  RAW ' + l.strip()[:175])
    import glob as _g, xml.etree.ElementTree as _E
    for _p in _g.glob(WORK + '/build/test-results/test/*.xml'):
        try: _r = _E.parse(_p).getroot()
        except Exception: continue
        for _c in _r.iter('testcase'):
            for _k in ('failure', 'error'):
                _n = _c.find(_k)
                if _n is not None: print('  XMLFAIL ' + (_n.get('message') or '')[:400])
    io.open(path, 'w', encoding='utf-8', newline='').write(orig)
    print('rolled back')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'docs: 1220 reason 1 was wrong -- random_enemy exists; the blocker is the fallback clause'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
