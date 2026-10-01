# -*- coding: utf-8 -*-
"""Round 545: ship relic 126 -- the sixth registry reclaim of this stretch is now unblocked."""

import glob
import io
import json
import os
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
REL = 'src/main/resources/relic_sets/126.json'
REGP = 'src/main/resources/relic_sets/_unmodelled.json'
TESTP = 'src/test/java/com/laosun/aluminium/test/RelicTriggerTableTest.java'

SRC = ('遗器 126 四件套: When the wearer becomes the target of another ally target\'s ability, gains 1 stack of '
       '"Help," stacking up to 2 time(s). If there are 2 stack(s) of "Help" when the wearer uses their Ultimate, '
       'consumes all "Help" to increase the wearer\'s ATK by 48% for 1 turn(s).')
NOTE_K = ('「装备者**成为其他我方目标的技能目标**时，获得 1 层【助力】，最多叠加 2 层」—— '
          '事件 **`CAST_SETUP`** ✓（⭐ 唯一"携带被瞄准者、且在结算之前"的事件 ✗ —— ⚠ `SkillExecutor` 实测 ✓，'
          '本段 `23048` 那条线的产物 ✓）＋ `target == self` ✓（⚠ 已出货写法 ✓：遗器 `105` ✓、光锥 `21016`/`22003` ✓）'
          '＋ `actor is_ally` ✓ ＋ **`actor != self`** ✗（⚠ "其他**我方**目标" ✗；'
          '⚠ **不能写 `!actor == self`** ✗ —— 引擎实测拒绝："negates a condition that does not read a party" ✓）；'
          '计数器用 `ADD_STACK` ✓（⚠ 照 `324` 的写法 ✓：`buff` / `amount`(Double) / `max_stacks` ✓，⛔ 无需注册 buff ✓）；'
          '⚠ `permanent: true` 是【助力】**跨回合保留**到终结技 ✓（⚠ 与 `324` 的 `until: turn_end` 有意不同 ✓）。'
          '⚠ **旧登记理由已被推翻** ✗：它称需要"成为我方技能目标"这一概念 ✗ —— ⚠ 实测 `target == self` 早已出货 ✓。')
NOTE_P = ('「施放终结技时，若持有 2 层【助力】，消耗所有【助力】，使装备者攻击力提高 48%，持续 1 回合」—— '
          '`ULT_CAST` ✓ ＋ `actor == self` ✓ ＋ `self_stacks:助力 >= 2.0` ✓（⚠ 整数阈值写 `.0` ✓）'
          '⇒ `REMOVE_STACK 2` ✓ ＋ `MODIFY_ATTR ATTACK 0.48`、`turns: 1` ✓；'
          '数值取自 `AbilityParamList = [2, 0.48, 1]` ✓。⚠ 二件套「暴击伤害提高 16%」属 `properties` 的 '
          '`CriticalDamageBase`（`param[0] = 0.16` ✓）⇒ **本文件不重写** ✓。')

data = {'4': [
    {'on': 'CAST_SETUP', 'id': 'relic126_help_stack',
     'when': ['target == self', 'actor is_ally', 'actor != self'],
     'do': [{'op': 'ADD_STACK', 'buff': '助力', 'amount': 1.0, 'max_stacks': 2,
             'permanent': True, 'target': 'self'}],
     'source': SRC, 'note': NOTE_K},
    {'on': 'ULT_CAST', 'id': 'relic126_help_payout',
     'when': ['actor == self', 'self_stacks:助力 >= 2.0'],
     'do': [{'op': 'REMOVE_STACK', 'buff': '助力', 'amount': 2.0, 'target': 'self'},
            {'op': 'MODIFY_ATTR', 'attribute': 'ATTACK', 'percent': 0.48, 'turns': 1, 'target': 'self'}],
     'source': SRC, 'note': NOTE_P},
]}
io.open(WORK + '/' + REL, 'w', encoding='utf-8', newline='').write(
    json.dumps(data, ensure_ascii=False, indent=2) + '\n')
print('relic 126 written (%d rules)' % len(data['4']))

reg_path = WORK + '/' + REGP
test_path = WORK + '/' + TESTP
reg_orig = io.open(reg_path, encoding='utf-8').read()
test_orig = io.open(test_path, encoding='utf-8').read()
reg = json.loads(reg_orig)
if '126' in reg:
    del reg['126']
    io.open(reg_path, 'w', encoding='utf-8', newline='').write(
        json.dumps(reg, ensure_ascii=False, indent=2) + '\n')
print('registry: 126 removed, still registered = %s' % sorted(reg.keys()))
tst = test_orig
m = re.search(r'(AUTHORED\s*=\s*[^\n]*?Set\.of\(\s*)', tst)
if m and '"126/4"' not in tst:
    tst = tst[:m.end()] + '"126/4", ' + tst[m.end():]
if tst.count('STILL_REGISTERED = 2') == 1:
    tst = tst.replace('STILL_REGISTERED = 2', 'STILL_REGISTERED = 1')
    print('STILL_REGISTERED 2 -> 1')
else:
    print('WARNING: STILL_REGISTERED = 2 not found uniquely: %d' % tst.count('STILL_REGISTERED = 2'))
if tst != test_orig:
    io.open(test_path, 'w', encoding='utf-8', newline='').write(tst)


def run(*a):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + ['--quiet', '--console=plain'], cwd=WORK,
                          capture_output=True, text=True, encoding='utf-8', errors='replace')


for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
    os.remove(p)
suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    for l in ((suite.stdout or '') + (suite.stderr or '')).strip().split('\n')[-14:]:
        print('  RAW ' + l.strip()[:175])
    import xml.etree.ElementTree as ET
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
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
    io.open(test_path, 'w', encoding='utf-8', newline='').write(test_orig)
    if os.path.exists(WORK + '/' + REL):
        os.remove(WORK + '/' + REL)
    print('rolled back')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: ship relic 126 -- another ally targeting the wearer, and the Help payout'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
