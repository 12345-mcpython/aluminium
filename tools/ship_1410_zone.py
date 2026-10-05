# -*- coding: utf-8 -*-
"""Round 1095: 1410's ultimate opens a zone -- two same-named debuffs, so both end together.

The old note was right that a plain `turns: 3` would leave the two debuffs behind after the zone ended. The answer
is not a new op: it is the SAME NAME, which is what `REMOVE_STATE`/the anchor sweep key on. Both debuffs carry
`buff: 结界`, the same `turns: 3`, and `ticksOn: "self"` -- so they expire together, and 「当海瑟音陷入无法战斗
状态时，结界也会被解除」 rides on `removeBuffsAnchoredTo` (that path was repaired in `b275250`).
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
PATH = WORK + '/src/main/resources/characters/1410.json'
RID = 'ult_zone_opens_and_lowers'
ZONE = '结界'                      # 结界

NOTE = (
    '⭐ 2026-09-30 转正 ✓：「海瑟音**展开结界**，使敌方目标'
    '攻击力降低 15%、防御力降低 25%…结界持续 3 回合，'
    '自身每回合开始时减 1。当海瑟音陷入无法战斗状态时，'
    '结界也会被解除」—— ⚠ 旧登记担心“用普通的 `turns: 3` 写'
    '会在**结界结束后**仍留着” ✗。⭐ 答案不是新 op，而是**同名** ✓：'
    '两句都带 `buff: 结界` + 同一个 `turns: 3` + `ticksOn: self` ⇒ 时间一到两句一起没 ✓；'
    '而「她倒下时结界也被解除」由 `removeBuffsAnchoredTo` 承担 ✓'
    '（⚠ 同样依赖 (b)：`ticks_on` 直到 `b275250` 才真正接在 `MODIFY_ATTR` 上 ✓）。'
)


def main():
    doc = json.load(io.open(PATH, encoding='utf-8'))
    rules = doc if isinstance(doc, list) else doc.get('rules')
    if rules is None:
        print('REFUSING: no rules list')
        return 1
    if any(isinstance(r, dict) and r.get('id') == RID for r in rules):
        print('already shipped: %s' % RID)
        return 0
    do = [
        {'op': 'MODIFY_ATTR', 'attribute': 'ATTACK', 'percent': -0.15, 'turns': 3,
         'buff': ZONE, 'ticks_on': 'self', 'target': 'all_enemies'},
        {'op': 'MODIFY_ATTR', 'attribute': 'DEFENCE', 'percent': -0.25, 'turns': 3,
         'buff': ZONE, 'ticks_on': 'self', 'target': 'all_enemies'},
    ]
    rules.append({'on': 'ULT_CAST', 'id': RID, 'when': ['actor == self'], 'do': do, 'note': NOTE})
    text = json.dumps(doc, ensure_ascii=False, indent=2) + chr(10)
    json.loads(text)
    io.open(PATH, 'w', encoding='utf-8', newline='').write(text)
    print('shipped %s (rules now %d)' % (RID, len(rules)))
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
                    print('  FAIL %s: %s' % (c.get('name'), (m.get('message') or '')[:280]))
    print('REFUSING to commit')
    sys.exit(1)
print('gates %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', 'src/main/resources/characters/1410.json'], cwd=WORK, check=True)
subprocess.run(['git', 'commit', '-q', '-m',
                "content: 1410's zone opens with two same-named debuffs, so they end together"],
               cwd=WORK, check=True)
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
