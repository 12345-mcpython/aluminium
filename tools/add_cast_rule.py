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
    '\u2b50 2026-09-30\uff1a\u7528 `CAST_SKILL` \u8ba9\u90a3\u4e2a\u5355\u4f4d**\u7acb\u5373\u65bd\u653e\u4e00\u6b21** `' + SLOT + '` \u2713 \u2014\u2014 '
    '\u26a0 \u8bfb\u7684\u662f**\u5b83\u81ea\u5df1\u7684\u6570\u636e\u884c** \u2713\uff08\u800c\u4e0d\u662f\u624b\u5199\u500d\u7387 \u2717\uff09\uff0c'
    '\u26a0 \u800c\u62a4\u680f\u4f1a\u62d2\u7edd**\u4e0d\u9020\u6210\u4f24\u5bb3**\u7684\u6280\u80fd \u2717\u3002'
    '\u26a0 \u6765\u6e90\uff1a`CAST_SKILL` \u662f `commandSummon` \u653e\u5bbd\u4e09\u5904\u800c\u6765\uff08\u89c1 `aggro \u56de\u6536\u4e4b\u4e03\u767e\u516b\u5341\u4e94`\uff09\uff1b'
    '\u26a0 \u5b83\u4e0d\u7ecf\u8fc7\u6218\u6280\u70b9\u6d88\u8017 \u2713\u3002'
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
