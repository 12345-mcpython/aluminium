# -*- coding: utf-8 -*-
"""Round 622: the first shipped reader of `times` -- 8005's skill clause.

「并额外造成 4 次伤害，每次对随机敌方单体造成 50% 攻击力虚数伤害」. Four extra settlements, each drawing its own
random enemy: DAMAGE + scale self_attr:ATTACK + percent 0.5 + element Imaginary + target random_enemy + times 4.
The event is DEALING_DAMAGE with from_skill SKILL and the damage_is_attack guard, following 1109's pattern (without
the guard the extra instance would re-trigger this same rule).
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/8005.json'

RULE = {
    'on': 'DEALING_DAMAGE',
    'id': 'skill_extra_hits_random',
    'when': ['actor == self', 'from_skill SKILL', 'damage_is_attack'],
    'do': [{'op': 'DAMAGE', 'scale': 'self_attr:ATTACK', 'percent': 0.5,
            'element': 'Imaginary', 'target': 'random_enemy', 'times': 4}],
    'source': ('8005 开拓者·虚数 战技: 「并额外造成 4 次伤害，每次对随机敌方单体造成 50% 攻击力虚数伤害。」'),
    'note': ('⭐ **`times` 的第一个读者** ✓（本日新增的效果级重复 ✓）。四件事合作 ✓：'
             '① 事件 `DEALING_DAMAGE` ✓ ＋ `from_skill SKILL` ✓（「战技」✓）；'
             '② **`damage_is_attack` 护栏** ✓（⚠ 不可省 ✗ —— 追加实例本身也是一次 `DEALING_DAMAGE` ✓，'
             '⚠ 没有护栏会自我触发到递归上限 ✗，写法照 1109 的同族条款 ✓）；'
             '③ `DAMAGE` ＋ 字面倍率 `self_attr:ATTACK` **0.5** ✓ ＋ `element: Imaginary` ✓（虚数 ✓）；'
             '④ ⭐ `target: random_enemy` ＋ **`times: 4`** ✓ —— ⚠ `times` 把**整次结算**重复 4 遍 ✓，'
             '⚠ 而 `resolveTargets` 在重复循环**内部**被调用 ✓ ⇒ **每次都会重抽一个随机敌方目标** ✓ '
             '（⚠ 这正是「每次对随机敌方单体」的意思 ✓，⚠ 不是把同一个目标打 4 次 ✗）。'
             '⚠ 数值 4 与 50% 取自本文档原文 ✓（战技段 ✓）。'),
}

path = WORK + '/' + CHAR
doc = json.load(io.open(path, encoding='utf-8'))
if isinstance(doc, list):
    where = doc
elif isinstance(doc, dict) and isinstance(doc.get('rules'), list):
    where = doc['rules']
else:
    print('REFUSING: unknown top-level shape (%s)' % type(doc).__name__)
    sys.exit(1)
if any(isinstance(r, dict) and r.get('id') == RULE['id'] for r in where):
    print('already shipped')
    sys.exit(0)
if any(isinstance(r, dict) and 'times' in json.dumps(r, ensure_ascii=False) for r in where):
    print('REFUSING: a rule already uses times')
    sys.exit(1)
where.append(RULE)
text = json.dumps(doc, ensure_ascii=False, indent=2) + '\n'
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('8005 rule added (%d rules now), and the file still parses as JSON' % len(where))


def run(*a):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + ['--quiet', '--console=plain'], cwd=WORK,
                          capture_output=True, text=True, encoding='utf-8', errors='replace')


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
                      'content: 8005 skill -- four extra hits, each on a random enemy (times first reader)'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
