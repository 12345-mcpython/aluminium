# -*- coding: utf-8 -*-
"""Round 915: give 23050 its weakness-triggered clause -- two rules per rank, appended, not replacing."""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CONE = WORK + '/src/main/resources/light_cones/23050.json'

NOTE_GAIN = (
    '「装备者为敌方目标**添加弱点**时，恢复 1 个战技点，该效果**最多触发 1 次**，**施放终结技时重置**可触发次数」'
    '—— ① 事件 `WEAKNESS_ADDED` ✓（本段新造：`TriggerEvent` 声明 ＋ `ADD_ELEMENTAL_WEAKNESS` 的成功分支发射；'
    '⚠ **先问 `isWeakTo` 再改再发** ⇒ 重复添加不点着 ✓）；'
    '② `GAIN_SKILL_POINT amount: 1 target: self` ✓；'
    '③ 「最多触发 1 次」用 **`once_per_battle`** ✓（实测 `TriggerSpec:123` ✓ —— ⚠ 不是 `cooldown`，'
    '那是「每 N 回合」✗，而 `21038` 的登记表为这个区别订正过一次 ✓）；'
    '④ 「施放终结技时重置」用 **`RESET_TRIGGER_LIMIT`** ✓（本段第 13 件出货，读者 1403／1305／1207 同形态 ✓）。'
)
NOTE_RESET = (
    '「施放终结技时重置可触发次数」—— 与上一条配对 ✓：`RESET_TRIGGER_LIMIT` 需要 `rule` 指名目标规则的 **id** ✓，'
    '⚠ 而限制键是 `source#index` ✗ ⇒ 引擎用 `TriggerTable.keyOf` 把 id 换成键 ✓，⚠ 认不出就**抛** ✓'
    '（一个什么都不清的重置是静默的错 ✓）。'
)
GAIN_ID = 'cone23050_weakness_grants_skill_point'
RESET_ID = 'cone23050_resets_the_weakness_count'

doc = json.load(io.open(CONE, encoding='utf-8'))
print('before: %s' % {k: [r.get('id') for r in doc[k]] for k in sorted(doc)})
for rank in sorted(doc):
    ids = [r.get('id') for r in doc[rank]]
    if GAIN_ID in ids or RESET_ID in ids:
        print('rank %s already carries the pair -- refusing' % rank)
        sys.exit(1)
    doc[rank].append({
        'on': 'WEAKNESS_ADDED',
        'id': GAIN_ID,
        'when': ['actor == self'],
        'do': [{'op': 'GAIN_SKILL_POINT', 'amount': 1, 'target': 'self'}],
        'once_per_battle': True,
        'note': NOTE_GAIN,
    })
    doc[rank].append({
        'on': 'ULT_CAST',
        'id': RESET_ID,
        'when': ['actor == self'],
        'do': [{'op': 'RESET_TRIGGER_LIMIT', 'rule': GAIN_ID, 'target': 'self'}],
        'note': NOTE_RESET,
    })
text = json.dumps(doc, ensure_ascii=False, indent=2) + chr(10)
json.loads(text)
io.open(CONE, 'w', encoding='utf-8', newline='').write(text)
print('after : %s' % {k: [r.get('id') for r in doc[k]] for k in sorted(doc)})


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
gates = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % gates)
subprocess.run(['git', 'add', 'src/main/resources/light_cones/23050.json'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 23050 gains the weakness-triggered skill point, once per battle, reset by the ultimate'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
