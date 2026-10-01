# -*- coding: utf-8 -*-
"""Round 554: ship light cone 21029 (后会有期 / 交手如交谈) and reclaim its registry entry.

Authoritative sources: EquipmentConfig 21029 -> SkillID 21029 -> EquipmentSkillConfig (five ranks)
-> TextMap/TextMapCHS.json: 「装备者施放普攻或战技后，对随机 1 个受到攻击的敌方目标造成等同于自身 #1[i]%
攻击力的附加伤害。」  ParamList per rank: [0.48] [0.6] [0.72] [0.84] [0.96].
Shape: light_cones/23007.json (ATTACK_FINISHED + target random_hit_enemy inside the do entry),
       light_cones/21030.json (ADD_DAMAGE + percent + scale self_attr:<attr>).
"""

import io
import json
import os
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
REL = 'src/main/resources/light_cones/21029.json'
REG = 'src/main/resources/light_cones/_unmodelled.json'

SRC = ('光锥 21029 后会有期 · 能力「交手如交谈」: 「装备者施放普攻或战技后，对随机 1 个受到攻击的敌方目标造成'
       '等同于自身 #1[i]% 攻击力的附加伤害。」')
NOTE = ('⚠ 本条的写法**全部来自既有出货内容**，并已按第 553 轮的新纪律**逐项 grep 过引擎** ✓：'
        '① **事件 `ATTACK_FINISHED`** ✓（⚠ 它携带本次攻击**已冻结的命中集合** ✓ —— 照光锥 `23007` ✓，'
        '该光锥正是在此事件上用 `random_hit_enemy` 共 8 次 ✓）；'
        '② **目标 `random_hit_enemy`** ✓ —— ⚠ 它**早已存在于 `TriggerInterpreter.TARGET_SELECTORS`** ✓'
        '（⚠ 我第 548 轮把它登记为"缺失能力"，**是错的** ✗）；写法照 `23007:30` ✓：'
        '`"target"` 与 `"op"` **同级、写在 `do` 条目内** ✓；'
        '③ **效果 `ADD_DAMAGE`** ✓ —— ⚠ 它**早已出货于 `21030`（五档）与 `20018`（五档）** ✓'
        '（⚠ 我第 550 轮仍以为缺它 ✗）；`percent` ＋ **`scale: "self_attr:ATTACK"`** ✓ 照 `21030:13-15` ✓；'
        '④ ⚠ 「**普攻或战技**」是**析取** ✗ ⇒ ⭐ 照遗器 `105` 的既有惯例写成**两条规则** ✓'
        '（⚠ 一条 `from_category NORMAL` ✓、一条 `BPSKILL` ✓）；'
        '⑤ 数值取自权威 `EquipmentSkillConfig` 的五档 `ParamList` ✓：0.48 / 0.6 / 0.72 / 0.84 / 0.96 ✓。'
        '⚠ 因此本张**不需要任何引擎改动** ✓。')

PERCENTS = {'1': 0.48, '2': 0.6, '3': 0.72, '4': 0.84, '5': 0.96}
data = {}
for tier, pct in PERCENTS.items():
    rules = []
    for cat, tag in (('NORMAL', 'normal'), ('BPSKILL', 'bpskill')):
        rules.append({
            'on': 'ATTACK_FINISHED',
            'id': 'cone21029_extra_damage_%s' % tag,
            'when': ['actor == self', 'from_category ' + cat],
            'do': [{'op': 'ADD_DAMAGE', 'percent': pct,
                    'scale': 'self_attr:ATTACK', 'target': 'random_hit_enemy'}],
            'source': SRC,
            'note': NOTE,
        })
    data[tier] = rules
io.open(WORK + '/' + REL, 'w', encoding='utf-8', newline='').write(
    json.dumps(data, ensure_ascii=False, indent=2) + '\n')
print('21029 written: %d tiers x 2 rules' % len(data))

reg_path = WORK + '/' + REG
reg_orig = io.open(reg_path, encoding='utf-8').read()
reg = json.loads(reg_orig)
if '21029' in reg:
    del reg['21029']
    io.open(reg_path, 'w', encoding='utf-8', newline='').write(
        json.dumps(reg, ensure_ascii=False, indent=2) + '\n')
print('registry: 21029 removed, still registered = %s' % sorted(reg.keys()))


def run(*a):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + ['--quiet', '--console=plain'], cwd=WORK,
                          capture_output=True, text=True, encoding='utf-8', errors='replace')


for p in __import__('glob').glob(WORK + '/build/test-results/test/*.xml'):
    os.remove(p)
suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    for l in ((suite.stdout or '') + (suite.stderr or '')).strip().split('\n')[-14:]:
        print('  RAW ' + l.strip()[:175])
    import xml.etree.ElementTree as ET
    for p in __import__('glob').glob(WORK + '/build/test-results/test/*.xml'):
        try:
            root = ET.parse(p).getroot()
        except Exception:
            continue
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                n = c.find(k)
                if n is not None:
                    print('  FAIL ' + (n.get('message') or '')[:300].replace('\n', ' '))
    io.open(reg_path, 'w', encoding='utf-8', newline='').write(reg_orig)
    if os.path.exists(WORK + '/' + REL):
        os.remove(WORK + '/' + REL)
    print('rolled back')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: ship light cone 21029 -- random hit enemy, appended ATK-scaled damage'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
