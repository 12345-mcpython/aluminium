# -*- coding: utf-8 -*-
"""Round 880: build light_cones/21021.json -- five tiers, one TURN_START clause each.

21021 was registered as unmodelled because it needed a "random ally below half energy" selector; that selector shipped
in 4a6c412. The five tiers differ only in how much energy is restored (8/10/12/14/16); the 50% threshold is the
text own and identical in every tier. Base stats (ATK 19.2 / DEF 18 / HP 43.2) are NOT written here -- they come from
somewhere else (Weapon is a 149-line carrier). If the suite says otherwise, that tells us so.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CONE = 'src/main/resources/light_cones/21021.json'
NL = chr(10)
AMOUNT = {'1': 8, '2': 10, '3': 12, '4': 14, '5': 16}
SRC = '光锥 21021 等价交换 技能 酣适: 「当装备者的回合开始时，随机为1个当前能量百分比小于 50% 的我方其他目标恢复 8/10/12/14/16 点能量。」'
NOTE = ('⭐ 由登记表转正 ✓（2026-09-30 ✓）：⚠ 原先登记的原因是 ⚠ *TARGET_SELECTORS holds random_enemy and '
        'random_hit_enemy but NO random ally* ✗ ⇒ ⚠ 选择器 ⚠ `random_ally_below_half_energy` ✗ 已在 `4a6c412` 出货 ✓。'
        '⚠ 事件 ⚠ `TURN_START` ✗（⚠ 原文「回合开始时」✓）；⚠ `when: actor == self` ✗（⚠ 「**装备者**的回合开始时」✓）。'
        '⚠ 阈值 50% 与「排除装备者」**写死在选择器里** ✗ —— ⚠ 因为 ⚠ 五阶的 `50/50/50/50/50%` **完全相同** ✓，'
        '⚠ 且英文原文写明 ⚠ *excluding the wearer* ✓。⛔ **未写的是基础属性** ✗（⚠ 攻 19.2／防 18／生 43.2 ✓）：'
        '⚠ 它们**不是**这个文件的规则 ✓（⚠ `Weapon` 只有 149 行 ✓ —— ⚠ 那是载体，⚠ 不是来源 ✓）。')

doc = {}
for rank, amount in AMOUNT.items():
    doc[rank] = [{
        'on': 'TURN_START',
        'id': 'talent_energy_for_low_ally',
        'when': ['actor == self'],
        'do': [{'op': 'GAIN_ENERGY', 'amount': amount, 'target': 'random_ally_below_half_energy'}],
        'source': SRC,
        'note': NOTE,
    }]
path = WORK + '/' + CONE
text = json.dumps(doc, ensure_ascii=False, indent=2) + NL
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('21021.json written: %d ranks, %d rule each' % (len(doc), len(doc['1'])))


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
                                               (m.get('message') or '')[:300]))
    for l in ((s.stdout or '') + (s.stderr or '')).split(NL):
        if '.java:' in l or '错误:' in l:
            print('  DIAG ' + l.strip()[:180])
    print('KEPT the file: a red suite here is information (see the failure messages), not a rollback cue')
    sys.exit(1)
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', CONE], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 21021 ships the turn-start energy clause now that random_ally exists'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
