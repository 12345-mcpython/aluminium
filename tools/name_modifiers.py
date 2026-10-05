# -*- coding: utf-8 -*-
"""Name the un-named modifiers in a newest-only rule, so REMOVE_STATE can take them off too.

TriggerInterpreter:2241 -- "A NAMED modifier is what REMOVE_STATE can take off". A rule that applies both a state
(APPLY_BUFF, named) and a plain MODIFY_ATTR (un-named) only transfers HALF of itself when the state is stripped:
the modifier stays on the old target. 1202's ATTACK +50% and 1224's SPEED +10% are exactly that.

Usage: python tools/name_modifiers.py <cid> <state-name> [<state-name> ...]
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'

if len(sys.argv) < 3:
    print(__doc__)
    sys.exit(1)
CID = sys.argv[1]
NAMES = sys.argv[2:]
PATH = '%s/src/main/resources/characters/%s.json' % (WORK, CID)

# ⚠ 任何"挂在场上、按名字可摘"的修饰器。DAMAGE/GAIN_RESOURCE 这类是**瞬时**的，不属于这里。
MODIFIER_OPS = {'MODIFY_ATTR', 'MODIFY_DAMAGE_TAKEN', 'MODIFY_DAMAGE_DEALT', 'MODIFY_RULE'}

NOTE = (
    '⭐ 2026-09-30 补完整：上一次只转移了**一半** ✗ —— ⚠ '
    '`REMOVE_STATE` 按**名字**摘，而本条里除了 `APPLY_BUFF` 还有一个**不具名**的 '
    '`MODIFY_ATTR` ✗ ⇒ 它会留在旧目标身上 ✗。⚠ `TriggerInterpreter:2241` 的原话：'
    '*A NAMED modifier is what REMOVE_STATE can take off* ✓ ⇒ 现在给它同名 `buff` ✓，'
    '于是一条 `REMOVE_STATE` 同时摘掉**状态与数值** ✓。'
)

doc = json.load(io.open(PATH, encoding='utf-8'))
rules = doc if isinstance(doc, list) else doc.get('rules')
if rules is None:
    print('REFUSING: no rules list')
    sys.exit(1)

changed = []
for r in rules:
    if not isinstance(r, dict):
        continue
    applied = [e for e in (r.get('do') or []) if e.get('op') == 'APPLY_BUFF' and str(e.get('buff')) in NAMES]
    if not applied:
        continue
    name = str(applied[0].get('buff'))
    for e in (r.get('do') or []):
        # ⚠ 口径要完整：任何**留在场上**的修饰器都按名字摘，不只是 MODIFY_ATTR。
        # 实测 1112 的规则里有 MODIFY_DAMAGE_TAKEN，而这一行原先只找 MODIFY_ATTR ⇒ 它误报"没事可做"。
        if e.get('op') in MODIFIER_OPS and not e.get('buff'):
            e['buff'] = name
            changed.append('%s.%s <- %s' % (r.get('id'), e.get('attribute') or e.get('op'), name))
    r['note'] = str(r.get('note') or '') + chr(10) + chr(10) + NOTE

print('named modifiers: %s' % changed)
if not changed:
    print('nothing to do')
    sys.exit(0)
text = json.dumps(doc, ensure_ascii=False, indent=2) + chr(10)
json.loads(text)
io.open(PATH, 'w', encoding='utf-8', newline='').write(text)


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
            r2 = E.parse(p).getroot()
        except Exception:
            continue
        for c in r2.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    print('  FAIL %s: %s' % (c.get('name'), (m.get('message') or '')[:280]))
    print('REFUSING to commit')
    sys.exit(1)
print('gates %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', 'src/main/resources/characters/%s.json' % CID], cwd=WORK, check=True)
subprocess.run(['git', 'commit', '-q', '-m',
                "content: %s's newest-only rule names its modifiers, so the whole effect transfers" % CID],
               cwd=WORK, check=True)
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
