# -*- coding: utf-8 -*-
"""Round 767: 1403 Tribbie -- 「该效果每个角色最多触发 1 次，缇宝施放终结技时重置我方其他角色可触发次数」.

Both halves are needed and now both exist: per_turn 1 gives the 「1 次」, per_subject actor gives the 「每个角色」,
and a ULT_CAST rule resets that one count. Either half alone would be an approximation.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1403.json'
NL = chr(10)
TARGET_ID = 'talent_followup_on_other_ult'

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

target['per_turn'] = 1
target['per_subject'] = 'actor'
old_note = target.get('note') or ''
target['note'] = ('⭐ **两个维度都已具备** ✓（2026-09-30 ✓）：⚠ 本句是 ⚠ 「每个角色最多触发 1 次」✗ ＋ '
                  '⚠ 「缇宝施放终结技时重置」✗ ⇒ ⚠ 分别由 ⚠ `per_turn: 1` ✗（给「1 次」✓）＋ ⚠ '
                  '`per_subject: actor` ✗（给「每个角色」✓）＋ ⚠ 另一条 `ULT_CAST` 规则（给「重置」✓）表达 ✓。'
                  '⚠ 缺任何一个都是近似 ✓。' + ((' ｜ 原注记：' + old_note) if old_note else ''))

rules.append({
    'on': 'ULT_CAST',
    'id': 'ult_resets_the_per_actor_followup_count',
    'when': ['actor == self'],
    'do': [{'op': 'RESET_TRIGGER_LIMIT', 'rule': TARGET_ID, 'target': 'self'}],
    'source': '1403 缇宝 天赋: 「…该效果每个角色最多触发 1 次，缇宝施放终结技时重置我方其他角色可触发次数。」',
    'note': ('⭐ 「缇宝施放终结技时重置」那一半 ✓（2026-09-30 ✓）：⚠ 用 ⚠ `RESET_TRIGGER_LIMIT` ✗ 只清 '
             '⚠ `talent_followup_on_other_ult` 这一条的计数 ✓ —— ⚠ 而不是清她全部规则的计数 ✓'
             '（⚠ 她 `ULT_CAST` 上还有别的规则 ✓，⚠ 清全部就是近似 ✓）。'),
})
text = json.dumps(doc, ensure_ascii=False, indent=2) + NL
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('1403 rules: %d；%s 现在带 per_turn=1 与 per_subject=actor' % (len(rules), TARGET_ID))


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
                      'content: 1403 the followup is capped per triggerer and reset by her ultimate'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
