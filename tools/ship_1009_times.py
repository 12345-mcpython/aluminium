# -*- coding: utf-8 -*-
"""Round 642: 1009 (Asta) -- the third reader of `times`, plus the line its literals list should gain.

The clause comes from her own file's registration (round 614's read): 「并额外造成 4 次伤害，每次对随机敌方单体造成
50% 攻击力火伤」. The pre-flight grep (rounds 638-641) cleared AstaTest and showed that
CharacterConditionLiteralsTest is a hand-maintained list with NO exhaustiveness check -- so a new rule cannot fail it,
but the list's stated intent ("every rule waits for exactly what it says") means it should gain a line anyway.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1009.json'
TEST = 'src/test/java/com/laosun/aluminium/test/CharacterConditionLiteralsTest.java'

RULE = {
    'on': 'DEALING_DAMAGE',
    'id': 'skill_extra_hits_random',
    'when': ['actor == self', 'from_skill SKILL', 'damage_is_attack'],
    'do': [{'op': 'DAMAGE', 'scale': 'self_attr:ATTACK', 'percent': 0.5,
            'element': 'Fire', 'target': 'random_enemy', 'times': 4}],
    'source': '1009 艾丝妲 战技: 「并额外造成 4 次伤害，每次对随机敌方单体造成 50% 攻击力火属性伤害。」',
    'note': ('⭐ **`times` 的第三个读者** ✓（本段第六能力「效果级重复」✓）。'
             '⚠ 写法与 `8005`/`8006` 同形 ✓：`from_skill SKILL` ✓ ＋ **`damage_is_attack`** ✗'
             '（⚠ 不可省 —— 追加实例本身也是一次 `DEALING_DAMAGE` ✓，⚠ 无护栏会撞递归上限 ✓，第 630 轮实测过 ✓）'
             '＋ `target: random_enemy` ＋ **`times: 4`** ✓。⚠ 数值 **4 次**与 **50%** 取自她自己的文档原文 ✓'
             '（⚠ 登记在 `1009.json` 的 M-32 注记里 ✓，第 614 轮读到 ✓）。'),
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
    print('1009 rule added (%d rules now)' % len(where))
else:
    print('1009 already has it')

tpath = WORK + '/' + TEST
t = io.open(tpath, encoding='utf-8').read()
anchor = 'assertRule(1009, "BATTLE_START", "level_convention", List.of());'
line = ('        assertRule(1009, "DEALING_DAMAGE", "skill_extra_hits_random",\n'
        '                List.of("actor == self", "from_skill SKILL", "damage_is_attack"));')
print('literals anchor count: %d' % t.count(anchor))
if t.count(anchor) != 1:
    print('REFUSING: the literals anchor is not unique')
    subprocess.run(['git', 'checkout', '--', CHAR], cwd=WORK)
    sys.exit(1)
if 'skill_extra_hits_random' not in t:
    t = t.replace(anchor, anchor + '\n' + line, 1)
    io.open(tpath, 'w', encoding='utf-8', newline='').write(t)
    print('literals list gained the new rule (voluntarily; it has no exhaustiveness check)')
else:
    print('literals list already names it')


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
                                                  (m.get('message') or '')[:220]))
    subprocess.run(['git', 'checkout', '--', CHAR, TEST], cwd=WORK)
    print('ROLLED BACK the rule and the literals line')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 1009 skill extra hits via times, and its line in the condition literals list'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
