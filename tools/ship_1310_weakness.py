# -*- coding: utf-8 -*-
"""Round 729: 1310 Firefly -- the technique adds a Fire weakness to every enemy at each wave. Second reader of the op."""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1310.json'
TEST = 'src/test/java/com/laosun/aluminium/test/CharacterConditionLiteralsTest.java'
NL = chr(10)
TOUCHED = [CHAR, TEST]

NOTE = ('⭐ 弱点植入的**波次那半已出货** ✓（2026-09-30 ✓）：⚠ 原登记写 **弱点植入 ✗ ＋ 按波次 ✗** ✓ ⇒ ⚠ 现在两者'
        '都有 ✓ —— ⚠ `WAVE_START` ✓（⚠ `23059` 的注记用过它 ✓）＋ ⚠ `ADD_ELEMENTAL_WEAKNESS` ＋ '
        '⚠ `element: Fire` ＋ ⚠ `target: all_enemies` ✗。⛔ **仍登记的是时长** ✗：⚠ 文档写 **持续 2 回合** ✗，'
        '⚠ 而 `Enemy.stanceWeak` 是**无寿命的集合** ✓ ⇒ ⚠ 加上的弱点不会自己消失 ✓（⚠ 差异已写明 ✓）。'
        '⚠ 本条**只写秘技那一句** ✓；⚠ 强化普攻与强化战技里的**火弱点** ⚠ 各自要自己的事件与目标 ✗ ⇒ '
        '⚠ 仍登记 ✓。⚠ 引擎与判据：⚠ `Enemy.addWeakness` ✓／`ADD_ELEMENTAL_WEAKNESS` ✓／`WeaknessOpTest` ✓。')
assert '"' not in NOTE and "'" not in NOTE

RULE = {
    'on': 'WAVE_START',
    'id': 'technique_adds_fire_weakness',
    'when': ['self has_state 秘技'],
    'do': [{'op': 'ADD_ELEMENTAL_WEAKNESS', 'element': 'Fire', 'target': 'all_enemies'}],
    'source': '1310 流萤 秘技: 「每个波次开始时为敌方全体添加火属性弱点，持续 2 回合。」',
    'note': NOTE,
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
print('1310 rules: %d' % len(rules))

tpath = WORK + '/' + TEST
t = io.open(tpath, encoding='utf-8').read()
anchor = 'assertRule(1310, "BATTLE_START", "level_convention", List.of());'
line = '        assertRule(1310, "WAVE_START", "technique_adds_fire_weakness", List.of("self has_state 秘技"));'
print('literals anchor: %d' % t.count(anchor))
if t.count(anchor) != 1:
    subprocess.run(['git', 'checkout', '--', CHAR], cwd=WORK)
    print('REFUSING: anchor not unique; 1310 reverted')
    sys.exit(1)
if 'technique_adds_fire_weakness' not in t:
    io.open(tpath, 'w', encoding='utf-8', newline='').write(t.replace(anchor, anchor + NL + line, 1))
    print('literals list gained the rule')


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
                      'content: 1310 the technique adds a Fire weakness at each wave (duration stays registered)'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
