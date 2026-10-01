# -*- coding: utf-8 -*-
"""Round 701: 1305's fallback -- the content change, with the note's misspelling corrected.

Measured in round 700: `talent_chance_followup` targets `target`, NOT `random_hit_enemy` as the round-615 note claimed.
The guard added in round 699 (assert exactly one hit) is what caught the difference. The engine half shipped in 694.
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
hits = [e for e in (rule.get('do') or []) if isinstance(e, dict) and e.get('target') == 'target']
print('effects targeting the event target: %d' % len(hits))
if len(hits) != 1:
    print('REFUSING: expected exactly one')
    sys.exit(1)
hits[0]['target'] = 'target_else_random_enemy'
rule['note'] = (str(rule.get('note') or '') + ' ⭐ **退路已出货** ✓（2026-09-30 ✓）：⚠ 本条的 `target` 由 '
                '`target` ✗ 改为 **`target_else_random_enemy`** ✗ —— ⚠ 即 ⚠ 事件目标**未阵亡** ⇒ 打它 ✓；'
                '⚠ 已阵亡 ⇒ ⚠ 打一个存活的随机敌方 ✓（⚠ 与原句「若追加攻击施放前目标被消灭则对敌方随机单体发动」'
                '逐字对齐 ✓）。⚠ **订正一处旧账** ✗：⚠ 本条注记此前写作 `random_hit_enemy` ✗ —— ⚠ 那是**错的** ✓；'
                '⚠ 实测（2026-09-30，第 700 轮）⚠ 本条与 `ult_reaction_followup` **都打 `target`** ✓，'
                '⚠ 文件里从来没有 `random_hit_enemy` ✓。⚠ 引擎侧见 `TriggerInterpreter.resolveTarget` ✓'
                '（⚠ 谓词 `CanHit.isDeath()` ✓ —— ⚠ `CanHit` 第 88–90 行说明**无敌与阵亡正交** ✓，'
                '⚠ 所以无敌的 Boss 仍走首选 ✓）。⚠ **窗口真实存在**：⚠ `CanHit` 第 550 行 `isDeath()` alone does '
                'not remove anybody ✗ ⇒ ⚠ 已阵亡者仍留在 `battle.enemies` ✓。').strip()
text = json.dumps(doc, ensure_ascii=False, indent=2) + NL
json.loads(text)
io.open(WORK + '/' + CHAR, 'w', encoding='utf-8', newline='').write(text)
print('1305 target: %s' % hits[0]['target'])


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
                      'content: 1305 the talent follow-up falls back to a random enemy when its target is dead'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
