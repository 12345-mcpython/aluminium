# -*- coding: utf-8 -*-
"""Round 699: 1305's fallback clause -- the content half.

`talent_chance_followup` targeted `random_hit_enemy` (a draw from the hit set), while the clause says 「若追加攻击施放前
目标被消灭则对敌方随机单体发动」 -- the event's target if it is still alive, otherwise ANY random enemy. The engine
half (`target_else_random_enemy`) shipped in round 694. The judge needs TWO enemies and a killed preferred one, which
is recorded in GAPS rather than faked here.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1305.json'
RID = 'talent_chance_followup'
NL = chr(10)

doc = json.load(io.open(WORK + '/' + CHAR, encoding='utf-8'))
rules = doc if isinstance(doc, list) else doc.get('rules')
rule = next((r for r in rules if isinstance(r, dict) and r.get('id') == RID), None)
if rule is None:
    print('REFUSING: %s not found' % RID)
    sys.exit(1)
changed = 0
for e in rule.get('do') or []:
    if isinstance(e, dict) and e.get('target') == 'random_hit_enemy':
        e['target'] = 'target_else_random_enemy'
        changed += 1
print('effects retargeted: %d' % changed)
if changed != 1:
    print('REFUSING: expected exactly one random_hit_enemy target in %s' % RID)
    sys.exit(1)
rule['note'] = (str(rule.get('note') or '') + ' ⭐ **退路已出货** ✓（2026-09-30 ✓）：⚠ 本条的 `target` 由 '
                '`random_hit_enemy` ✗ 改为 **`target_else_random_enemy`** ✗ —— ⚠ 原句说「对敌方**随机单体**」✗ '
                '＝ **任意一个敌方** ✓，⚠ 而 `random_hit_enemy` 是"从本次命中集里随机" ✗ ⇒ ⚠ **顺带是一处语义修正** ✓。'
                '⚠ 新选择器的语义 ✓：⚠ 事件目标**未阵亡** ⇒ 打它 ✓；⚠ 已阵亡 ⇒ ⚠ 打一个存活的随机敌方 ✓。'
                '⚠ 引擎侧见 `TriggerInterpreter` 的 `resolveTarget` ✓（⚠ 谓词用 `CanHit.isDeath()` ✓ —— '
                '⚠ `CanHit` 第 88–90 行写明**无敌与阵亡正交** ✓，⚠ 所以无敌的 Boss 仍走首选 ✓，⚠ 与原句一致 ✓）。'
                '⚠ **该窗口真实存在** ✓：⚠ `CanHit` 第 550 行原文 —— `isDeath()` alone does not remove anybody ✗ '
                '⇒ ⚠ 已阵亡者**仍留在 `battle.enemies`** ✓。').strip()
text = json.dumps(doc, ensure_ascii=False, indent=2) + NL
json.loads(text)
io.open(WORK + '/' + CHAR, 'w', encoding='utf-8', newline='').write(text)
print('1305 content updated')


def bail(msg):
    subprocess.run(['git', 'checkout', '--', CHAR], cwd=WORK)
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
                                               (m.get('message') or '')[:200]))
    bail('suite red')
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 1305 the follow-up falls back to a random enemy when its target is dead'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
