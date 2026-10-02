# -*- coding: utf-8 -*-
"""Round 949: 1405's talent -- a random absent weakness for 3 turns. Both halves shipped in rounds 937/930."""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
PATH = WORK + '/src/main/resources/characters/1405.json'
RID = 'talent_random_weakness_on_hit'

NOTE = (
    '「每当那刻夏击中 1 次敌方目标后，为目标添加 **1 个随机属性弱点**，持续 **3 回合**，'
    '**优先添加目标尚未拥有的弱点**」—— ⭐ **2026-09-30 转正** ✓：三件都已具备 —— '
    '① 事件 `ALLY_ATTACK` ✓（本文件星魂 1 那条已在用 ✓，注释说「每次施放只发一次」✓）；'
    '② `element: "random_absent"` ✓（`cacb8b8` ✓：从 `DamageElement.values()` 里挑一个 `!isWeakTo` 的 ✓，'
    '⚠ 取不到就**什么都不做** ✓ —— 那正是「优先未拥有」在无候选时的语义 ✓）；'
    '③ `turns: 3` ✓（`95ff510` ✓：计时弱点按**目标自己的回合**递减 ✓）。'
    '⚠ 而「击中 1 次」用 `ALLY_ATTACK` 表达 ✓：它每次施放只发一次 ✓，'
    '⚠ 若文档指的是**每一段**伤害都算，那是另一件事 ✗ —— 本条的登记里原本没有这句，按现有事件取最近的读法 ✓。'
)

doc = json.load(io.open(PATH, encoding='utf-8'))
rules = doc if isinstance(doc, list) else doc.get('rules')
if rules is None:
    print('REFUSING: cannot find a rules list')
    sys.exit(1)
if any(isinstance(r, dict) and r.get('id') == RID for r in rules):
    print('already shipped')
else:
    rules.append({
        'on': 'ALLY_ATTACK',
        'id': RID,
        'when': ['actor == self'],
        'do': [{
            'op': 'ADD_ELEMENTAL_WEAKNESS',
            'element': 'random_absent',
            'turns': 3,
            'target': 'target',
        }],
        'note': NOTE,
    })
    text = json.dumps(doc, ensure_ascii=False, indent=2) + chr(10)
    json.loads(text)
    io.open(PATH, 'w', encoding='utf-8', newline='').write(text)
    print('shipped %s (rules now %d)' % (RID, len(rules)))


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
                    print('  FAIL %s: %s' % (c.get('name'), (m.get('message') or '')[:280]))
    print('REFUSING to commit')
    sys.exit(1)
print('gates %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', 'src/main/resources/characters/1405.json'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 1405 talent ships a random absent weakness for three turns'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
