# -*- coding: utf-8 -*-
"""Add a CAST_SKILL rule to a character -- the auto-cast family's shipping tool.

Usage: python tools/add_cast_rule.py <cid> <rule-id> <event> <skill-slot> <target> [when...]
   e.g. python tools/add_cast_rule.py 1504 ults_immediate_talent ULT_CAST TALENT target "actor == self"

CAST_SKILL runs the named slot's own data row as one cast by the resolved target. Its guardrail REFUSES a slot whose
effect is not damaging, so a wrong pick fails loudly on the first run instead of quietly doing nothing.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'

if len(sys.argv) < 6:
    print(__doc__)
    sys.exit(1)
CID, RID, EVENT, SLOT, TARGET = sys.argv[1:6]
WHEN = list(sys.argv[6:])
PATH = '%s/src/main/resources/characters/%s.json' % (WORK, CID)

NOTE = (
    '⭐ 2026-09-30：用 `CAST_SKILL` 让那个单位**立即施放一次** `' + SLOT + '` ✓ —— '
    '⚠ 读的是**它自己的数据行** ✓（而不是手写倍率 ✗），'
    '⚠ 而护栏会拒绝**不造成伤害**的技能 ✗。'
    '⚠ 来源：`CAST_SKILL` 是 `commandSummon` 放宽三处而来（见 `aggro 回收之七百八十五`）；'
    '⚠ 它不经过战技点消耗 ✓。'
)

doc = json.load(io.open(PATH, encoding='utf-8'))
rules = doc if isinstance(doc, list) else doc.get('rules')
if rules is None:
    print('REFUSING: no rules list')
    sys.exit(1)
if any(isinstance(r, dict) and r.get('id') == RID for r in rules):
    print('already shipped: %s' % RID)
    sys.exit(0)

rule = {'on': EVENT, 'id': RID, 'do': [{'op': 'CAST_SKILL', 'skill': SLOT, 'target': TARGET}], 'note': NOTE}
if WHEN:
    rule['when'] = WHEN
    # keep the author's field order familiar: on, id, when, do, note
    rule = {'on': EVENT, 'id': RID, 'when': WHEN, 'do': rule['do'], 'note': NOTE}
rules.append(rule)
text = json.dumps(doc, ensure_ascii=False, indent=2) + chr(10)
json.loads(text)
io.open(PATH, 'w', encoding='utf-8', newline='').write(text)
print('added %s: on=%s when=%s CAST_SKILL skill=%s target=%s' % (RID, EVENT, WHEN, SLOT, TARGET))


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
    print('REFUSING to commit -- a red suite here may be the guardrail saying "that slot is not damaging"')
    sys.exit(1)
print('gates %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', 'src/main/resources/characters/%s.json' % CID], cwd=WORK, check=True)
subprocess.run(['git', 'commit', '-q', '-m',
                "content: %s's %s casts %s right now" % (CID, RID, SLOT)], cwd=WORK, check=True)
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
