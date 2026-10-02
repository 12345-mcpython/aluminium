# -*- coding: utf-8 -*-
"""Round 772: 1305 Dr. Ratio -- 「最多触发 2 次…施放终结技后重置」, counted per marked TARGET.

The note on ult_marks_wisemans_folly already registered exactly this gap (「按目标」), and the front half's engine is
per_subject. ⚠ The documents never say whether the 2 is per turn -- per_turn is the only multi-fire cap the engine has,
so it is used AND the reading is registered rather than hidden.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1305.json'
NL = chr(10)
TARGET_ID = 'ult_reaction_followup'

path = WORK + '/' + CHAR
doc = json.load(io.open(path, encoding='utf-8'))
rules = doc if isinstance(doc, list) else doc.get('rules')
if not isinstance(rules, list):
    print('REFUSING: unknown shape')
    sys.exit(1)
target = next((r for r in rules if isinstance(r, dict) and r.get('id') == TARGET_ID), None)
if target is None:
    print('REFUSING: %s not found' % TARGET_ID)
    sys.exit(1)
if 'per_subject' in target:
    print('already shipped')
    sys.exit(0)

target['per_turn'] = 2
target['per_subject'] = 'target'
target['note'] = ('⭐ **计数已具备** ✓（2026-09-30 ✓）：⚠ 原注记登记 ⚠ *「最多触发 2 次」＋「施放终结技后重置」✗'
                  '（按目标 ✓）* ⇒ ⚠ 现在是 ⚠ `per_turn: 2` ✗（给「2 次」✓）＋ ⚠ `per_subject: target` ✗'
                  '（给「**按目标**」✓ —— ⚠ 计数落在**被标记的那个敌人**身上 ✓）＋ ⚠ 另一条 `ULT_CAST` 规则'
                  '（给「重置」✓）✓。⛔ **仍登记的是量纲** ✗：⚠ 文档只说「最多触发 2 次」✗，⚠ **没有写"每回合"** ✓，'
                  '⚠ 而 ⚠ 引擎里唯一的多次上限是 ⚠ `per_turn` ✗ ⇒ ⚠ **"每回合 2 次"是我的解释** ✓（⚠ 差异写明 ✓，'
                  '⚠ 不装作等价 ✓）。')

rules.append({
    'on': 'ULT_CAST',
    'id': 'ult_resets_the_marked_target_count',
    'when': ['actor == self'],
    'do': [{'op': 'RESET_TRIGGER_LIMIT', 'rule': TARGET_ID, 'target': 'self'}],
    'source': '1305 真理医生: 「…【智者的短见】效果最多触发 2 次且仅对最新施放的目标生效。施放终结技后重置该效果触发次数。」',
    'note': ('⭐ 「施放终结技后重置」那一半 ✓（2026-09-30 ✓）：⚠ 用 ⚠ `RESET_TRIGGER_LIMIT` ✗ 只清 '
             '⚠ `ult_reaction_followup` 这一条的计数 ✓ —— ⚠ 他在 `ULT_CAST` 上还有 `ult_marks_wisemans_folly` ✓，'
             '⚠ 清全部就是近似 ✓。⚠ 而 ⚠ 被清的计数**记在被标记的敌人身上** ✗（⚠ `per_subject: target` ✓）'
             '⇒ ⚠ 换目标后另一个目标有自己的计数 ✓，⚠ 与「仅对最新施放的目标生效」一致 ✓。'),
})
text = json.dumps(doc, ensure_ascii=False, indent=2) + NL
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('1305 rules: %d；%s 现在带 per_turn=2 与 per_subject=target' % (len(rules), TARGET_ID))


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
                                               (m.get('message') or '')[:220]))
    bail('suite red')
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 1305 the reaction is capped per marked target and reset by his ultimate'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
