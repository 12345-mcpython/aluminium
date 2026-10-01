# -*- coding: utf-8 -*-
"""Round 726: 1315 Boothill -- the ultimate adds a Physical weakness. Content half of the thirteenth piece.

The engine (ded0bc5) and its judge (WeaknessOpTest) shipped already, so no new judge is needed. The documents say
「持续 2 回合」, and stanceWeak is a lifetimeless set, so only the addition is written and the duration is registered.
Pre-flight (rounds 724-725): four test files touch 1315 and none binds ULT_CAST -- BoothillTest fires SKILL_CAST,
BREAK and KILL only.
"""

import io
import json
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1315.json'
TEST = 'src/test/java/com/laosun/aluminium/test/CharacterConditionLiteralsTest.java'
NL = chr(10)
TOUCHED = [CHAR, TEST]

RULE = {
    'on': 'ULT_CAST',
    'id': 'ult_adds_physical_weakness',
    'when': ['actor == self'],
    'do': [{'op': 'ADD_ELEMENTAL_WEAKNESS', 'element': 'Physical', 'target': 'target'}],
    'source': '1315 波提欧 终结技: 「为指定敌方单体添加物理弱点，持续 2 回合。」',
    'note': ('⭐ **弱点植入已出货** ✓（2026-09-30 ✓）：⚠ 原先登记为 **弱点植入 ✗**（⚠ 与 1006/1408 同一族 ✓）'
             '⇒ ⚠ 现已写出 ⚠ **添加**那一半 ✓ —— ⚠ `ADD_ELEMENTAL_WEAKNESS` ＋ ⚠ `element: Physical` ✗。'
             '⛔ **仍登记的是时长** ✗：⚠ 文档写 ⚠ **持续 2 回合** ✗，⚠ 而 ⚠ `Enemy.stanceWeak` 是一个')
             '**无寿命的集合** ✓（⚠ 只在 `EnemyFactory` 里由怪物数据写入 ✓）⇒ ⚠ 写了"添加"之后，⚠ 那个弱点'
             '**不会自己消失** ✓（⚠ 差异已写明 ✓，⚠ 不装作等价 ✓）。⚠ 引擎侧：⚠ `Enemy.addWeakness` ✓')
             '（⚠ 经 `setStanceWeak` ✓，⚠ 遵守该类第 192 行的要求 ✓）＋ ⚠ `TriggerInterpreter` 的 '
             '`ADD_ELEMENTAL_WEAKNESS` ✓；⚠ 判据见 `WeaknessOpTest` ✓（⚠ 实测 ⚠ 施加前 `isWeakTo` 为假、'
             '施加后为真 ✓）。'),
}

path = WORK + '/' + CHAR
doc = json.load(io.open(path, encoding='utf-8'))
rules = doc if isinstance(doc, list) else doc.get('rules')
if not isinstance(rules, list):
    print('REFUSING: unknown shape')
    sys.exit(1)
if any(isinstance(r, dict) and r.get('id') == RULE['id'] for r in rules):
    print('already shipped')
    sys.exit(0)
rules.append(RULE)
text = json.dumps(doc, ensure_ascii=False, indent=2) + NL
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('1315 rules: %d (weakness rule added)' % len(rules))

tpath = WORK + '/' + TEST
t = io.open(tpath, encoding='utf-8').read()
anchor = 'assertRule(1315, "BATTLE_START", "level_convention", List.of());'
line = ('        assertRule(1315, "ULT_CAST", "ult_adds_physical_weakness", List.of("actor == self"));')
print('literals anchor: %d' % t.count(anchor))
if t.count(anchor) != 1:
    subprocess.run(['git', 'checkout', '--', CHAR], cwd=WORK)
    print('REFUSING: the literals anchor is not unique; 1315 reverted')
    sys.exit(1)
if 'ult_adds_physical_weakness' not in t:
    io.open(tpath, 'w', encoding='utf-8', newline='').write(t.replace(anchor, anchor + NL + line, 1))
    print('literals list gained the new rule')
else:
    print('literals list already names it')


def bail(msg):
    subprocess.run(['git', 'checkout', '--'] + TOUCHED, cwd=WORK)
    print('ROLLED BACK via git checkout (%s)' % msg)
    sys.exit(1)


def run(*a):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + ['--quiet', '--console=plain'], cwd=WORK,
                          capture_output=True, text=True, encoding='utf-8', errors='replace')


s = run('test', '--rerun-tasks')
print('suite %d' % s.returncode)
if s.returncode != 0:
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
                    print('  FAIL %s#%s %s' % (c.get('classname'), c.get('name'),
                                               (m.get('message') or '')[:220]))
    bail('suite red')
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 1315 the ultimate adds a Physical weakness (duration stays registered)'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
