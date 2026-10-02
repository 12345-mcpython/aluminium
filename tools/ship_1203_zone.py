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
WHAT = '\u767d\u82b1\u4e4b\u523b'          # 白花之刻
ZONE = '\u7ed3\u754c'                       # 结界

NOTE_OPEN = (
    '\u2b50 2026-09-30 \u8f6c\u6b63 \u2713\uff1a\u5929\u8d4b\u300c\u3010\u767d\u82b1\u4e4b\u523b\u3011**\u8fbe\u5230 2 \u5c42\u65f6'
    '**\u6d88\u8017\u5168\u90e8**\u5e76**\u5c55\u5f00\u7ed3\u754c**\u300d\u2014\u2014 \u7ed3\u754c**\u672c\u8eab\u6ca1\u6709\u6570\u503c** \u2713'
    '\uff08\u6587\u6863\u53ea\u8bf4\u5b83**\u5c55\u5f00**\uff0c\u6548\u679c\u5728\u4e0b\u4e00\u53e5 \u2713\uff09\u21d2 \u6240\u4ee5\u5b83\u53ea\u662f'
    '\u4e00\u4e2a**\u540d\u5b57** \u2713\uff0c\u7528 `APPLY_BUFF` \u6807\u8bb0\uff1b\u800c `has_state \u7ed3\u754c` \u73b0\u5728\u8bfb\u5f97\u5230\u5b83 \u2713'
    '\uff08(a) \u5df2\u843d\uff0c`cebe90f`\uff09\u3002\u26a0 \u5c42\u6570\u9608\u503c\u7528 `self_stacks:` \u2713\u3002'
)
NOTE_HEAL = (
    '\u2b50 2026-09-30 \u8f6c\u6b63 \u2713\uff1a\u300c\u5904\u4e8e\u7ed3\u754c\u4e2d\u7684\u4efb\u610f\u654c\u65b9\u76ee\u6807\u53d7\u5230\u653b\u51fb\u540e\uff0c'
    '**\u65bd\u653e\u653b\u51fb\u7684\u6211\u65b9\u76ee\u6807**\u56de\u590d 18.00% \u653b\u51fb\u529b + 240\u300d\u2014\u2014 \u26a0 \u56de\u590d\u7684\u662f'
    '**\u653b\u51fb\u8005** \u2713 \uff08\u4e0d\u662f\u7f57\u5239 \u2717\uff09\u21d2 `target: attacker` \u2713\uff1b\u6761\u4ef6\u662f\u76ee\u6807\u8eab\u4e0a\u6709\u7ed3\u754c \u2713\u3002'
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
