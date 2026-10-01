# -*- coding: utf-8 -*-
"""Round 609: 1104's note lists three items as still to do, and the same file implements all three.

  * item 3 says the eidolon gate field is still unread and will be filled in next round -- eidolon1_freeze_chance
    exists, uses MODIFY_RULE on skill_daunting_smite, and its own note names the min_eidolon gate.
  * item 5 says RAISE_SKILL_LEVEL plus a gate would do -- eidolon3_levels and eidolon5_levels exist.
  * item 6 says MODIFY_ATTR EFFECT_RESISTANCE plus a gate -- eidolon4_party_resistance exists.

Short fragments with a uniqueness assertion each, quote-free replacements, and a re-parse before writing.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1104.json'

PAIRS = [
    ('（门槛字段待读，下一轮照 1001 的写法补 ✓）',
     '（✅ **已写出** ✓：见本档 `eidolon1_freeze_chance` ✓，门槛用规则级字段 `min_eidolon` ✓ '
     '—— **2026-09-30 修订** ✓；⚠ 原写「待读，下一轮补」✗）'),
    ('⇒ `RAISE_SKILL_LEVEL` + 星魂门槛即可；',
     '⇒ ✅ **已写出** ✓：见本档 `eidolon3_levels` 与 `eidolon5_levels` ✓（**2026-09-30 修订** ✓）；'),
    ('⇒ `MODIFY_ATTR EFFECT_RESISTANCE percent 0.2 target all_allies` + 门槛；',
     '⇒ ✅ **已写出** ✓：见本档 `eidolon4_party_resistance` ✓（**2026-09-30 修订** ✓）；'),
]

path = WORK + '/' + CHAR
text = io.open(path, encoding='utf-8').read()
for old, new in PAIRS:
    print('anchor %r count: %d' % (old[:24], text.count(old)))
    if text.count(old) != 1:
        print('REFUSING: that anchor is not unique')
        sys.exit(1)
    if '"' in new or "'" in new:
        print('REFUSING: a replacement carries a quote')
        sys.exit(1)
for old, new in PAIRS:
    text = text.replace(old, new, 1)
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('1104 note revised in three places, and the file still parses as JSON')


def run(*a, q=True):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + (['--quiet'] if q else []) + ['--console=plain'],
                          cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')


suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
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
                    print('  XMLFAIL ' + (m.get('message') or '')[:300])
    print('NOT auto-rolled-back: revert with git checkout if needed')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'docs: 1104 -- three items its note called pending are implemented in the same file'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
