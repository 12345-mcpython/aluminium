# -*- coding: utf-8 -*-
"""Round 626: ship 8005's times reader AND update the pinned count in the same commit.

SiblingHarmonyTest#hisFileCarriesTheClauses pins 8005's per-event rule counts; the new rule lands on
DEALING_DAMAGE, so that pin moves 1 -> 2. This is the fifth time in this stretch that shipping content also
means updating a pinned list (relic census, cone registry, unit-discipline lists, 8005's counts).
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/8005.json'
TEST = 'src/test/java/com/laosun/aluminium/test/SiblingHarmonyTest.java'

RULE = {
    'on': 'DEALING_DAMAGE',
    'id': 'skill_extra_hits_random',
    'when': ['actor == self', 'from_skill SKILL', 'damage_is_attack'],
    'do': [{'op': 'DAMAGE', 'scale': 'self_attr:ATTACK', 'percent': 0.5,
            'element': 'Imaginary', 'target': 'random_enemy', 'times': 4}],
    'source': '8005 开拓者·虚数 战技: 「并额外造成 4 次伤害，每次对随机敌方单体造成 50% 攻击力虚数伤害。」',
    'note': ('⭐ **`times` 的第一个读者** ✓（本日新增的效果级重复 ✓）。四件事合作 ✓：'
             '① 事件 `DEALING_DAMAGE` ✓ ＋ `from_skill SKILL` ✓（「战技」✓）；'
             '② **`damage_is_attack` 护栏** ✓（⚠ 不可省 ✗ —— 追加实例本身也是一次 `DEALING_DAMAGE` ✓，'
             '⚠ 无护栏会自我触发到递归上限 ✗，写法照 1109 的同族条款 ✓）；'
             '③ `DAMAGE` ＋ 字面倍率 `self_attr:ATTACK` **0.5** ✓ ＋ `element: Imaginary` ✓；'
             '④ ⭐ `target: random_enemy` ＋ **`times: 4`** ✓ —— ⚠ `times` 把整次结算重复 4 遍 ✓，'
             '⚠ 而 `resolveTargets` 在重复循环**内部**被调用 ✓ ⇒ **每次重抽一个随机敌方目标** ✓'
             '（⚠ 这正是「每次对随机敌方单体」的意思 ✓）。'
             '⚠ **兄弟纪律** ✗：⚠ 「额外造成4次伤害」在 **8005 与 8006 两份文档里都有** ✓'
             '（本日实测 ✓，`aluminium_texts/8005_开拓者.md` 与 `8006_开拓者.md` ✓）⇒ '
             '⚠ 按 `SiblingHarmonyTest` 的宗旨（*mirrored where the two documents agree* ✓）'
             '**8006 也应当镜像这一条** ✗ —— ⚠ 该项**尚未完成** ✓，如实登记于此 ✓。'),
}

path = WORK + '/' + CHAR
doc = json.load(io.open(path, encoding='utf-8'))
where = doc if isinstance(doc, list) else doc.get('rules')
if not isinstance(where, list):
    print('REFUSING: unknown shape')
    sys.exit(1)
if not any(isinstance(r, dict) and r.get('id') == RULE['id'] for r in where):
    where.append(RULE)
    text = json.dumps(doc, ensure_ascii=False, indent=2) + '\n'
    json.loads(text)
    io.open(path, 'w', encoding='utf-8', newline='').write(text)
    print('8005 rule added (%d rules)' % len(where))
else:
    print('8005 rule already present')

tpath = WORK + '/' + TEST
t = io.open(tpath, encoding='utf-8').read()
old = '        Assertions.assertEquals(1, table.ruleCount(com.laosun.aluminium.enums.TriggerEvent.DEALING_DAMAGE));'
new = ('        // 2026-09-30: 2 -- 8005 gained the skill clause 「额外造成 4 次伤害，每次对随机敌方单体」 (times).\n'
       '        Assertions.assertEquals(2, table.ruleCount(com.laosun.aluminium.enums.TriggerEvent.DEALING_DAMAGE));')
print('pin anchor count: %d' % t.count(old))
if t.count(old) != 1:
    print('REFUSING: the pin anchor is not unique')
    sys.exit(1)
io.open(tpath, 'w', encoding='utf-8', newline='').write(t.replace(old, new, 1))
print('8005 DEALING_DAMAGE pin: 1 -> 2')


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
                    print('  XMLFAIL %s#%s %s' % (c.get('classname'), c.get('name'),
                                                  (m.get('message') or '')[:200]))
    print('NOT rolled back automatically')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 8005 skill extra hits via times, plus its pinned rule count'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
