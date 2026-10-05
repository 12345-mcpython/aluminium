# -*- coding: utf-8 -*-
"""Round 1098: 1203's 【白花之刻】 zone -- a stack threshold opens it, and hits inside it heal the attacker.

The zone itself carries NO numbers in the document: 「【白花之刻】达到 2 层时消耗全部并展开结界」 says only that it
opens, and the effect lives in the next clause -- 「处于结界中的任意敌方目标受到攻击后，施放攻击的我方目标回复
18.00% 攻击力 + 240」. So the zone can be a NAME (an APPLY_BUFF marker) rather than a container, which is exactly what
`has_state 结界` now reads (the (a) widening, `cebe90f`).
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
PATH = WORK + '/src/main/resources/characters/1203.json'
WHAT = '白花之刻'          # 白花之刻
ZONE = '结界'                       # 结界

NOTE_OPEN = (
    '⭐ 2026-09-30 转正 ✓：天赋「【白花之刻】**达到 2 层时'
    '**消耗全部**并**展开结界**」—— 结界**本身没有数值** ✓'
    '（文档只说它**展开**，效果在下一句 ✓）⇒ 所以它只是'
    '一个**名字** ✓，用 `APPLY_BUFF` 标记；而 `has_state 结界` 现在读得到它 ✓'
    '（(a) 已落，`cebe90f`）。⚠ 层数阈值用 `self_stacks:` ✓。'
)
NOTE_HEAL = (
    '⭐ 2026-09-30 转正 ✓：「处于结界中的任意敌方目标受到攻击后，'
    '**施放攻击的我方目标**回复 18.00% 攻击力 + 240」—— ⚠ 回复的是'
    '**攻击者** ✓ （不是罗刹 ✗）⇒ `target: attacker` ✓；条件是目标身上有结界 ✓。'
)


def main():
    doc = json.load(io.open(PATH, encoding='utf-8'))
    rules = doc if isinstance(doc, list) else doc.get('rules')
    if rules is None:
        print('REFUSING: no rules list')
        return 1
    have = {r.get('id') for r in rules if isinstance(r, dict)}
    added = []
    if 'talent_zone_opens_at_two_stacks' not in have:
        added.append({'on': 'SKILL_CAST', 'id': 'talent_zone_opens_at_two_stacks',
                      'when': ['actor == self', 'self_stacks:' + WHAT + ' >= 2'],
                      'do': [{'op': 'APPLY_BUFF', 'buff': ZONE, 'permanent': True, 'target': 'self'}],
                      'note': NOTE_OPEN})
    if 'talent_zone_heals_the_attacker' not in have:
        added.append({'on': 'ALLY_ATTACK', 'id': 'talent_zone_heals_the_attacker',
                      'when': ['actor is_other_ally', 'target has_state ' + ZONE],
                      'do': [{'op': 'HEAL', 'scale': 'owner_attack', 'percent': 0.18, 'amount': 240,
                              'target': 'attacker'}],
                      'note': NOTE_HEAL})
    if not added:
        print('already shipped')
        return 0
    rules.extend(added)
    text = json.dumps(doc, ensure_ascii=False, indent=2) + chr(10)
    json.loads(text)
    io.open(PATH, 'w', encoding='utf-8', newline='').write(text)
    print('shipped %d rule(s); rules now %d' % (len(added), len(rules)))
    return 2


changed = main()
if changed == 1:
    sys.exit(1)
if changed != 2:
    sys.exit(0)


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
                    print('  FAIL %s: %s' % (c.get('name'), (m.get('message') or '')[:300]))
    print('REFUSING to commit')
    sys.exit(1)
print('gates %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', 'src/main/resources/characters/1203.json'], cwd=WORK, check=True)
subprocess.run(['git', 'commit', '-q', '-m',
                "content: 1203's white-flower zone opens at two stacks and heals whoever hits inside it"],
               cwd=WORK, check=True)
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
