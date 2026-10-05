# -*- coding: utf-8 -*-
"""Round 831: the guard becomes our own fact (lastUltEnergySpent > 0), and the value is cleared after the ultimate.

The old guard trusted damage.getCastCategory() == ULTRA, i.e. a fact another component maintains -- and measured, it
does not hold on the castImmediate path, so the write was skipped and the clause read 0. Clearing after the settle is
what makes "> 0" a precise test rather than a leak.
"""

import glob
import io
import os
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
BATTLE = 'src/main/java/com/laosun/aluminium/Battle.java'
NL = chr(10)

t = io.open(WORK + '/' + BATTLE, encoding='utf-8').read()
old_guard = '        if (damage.getCastCategory() == com.laosun.aluminium.enums.SkillCategory.ULTRA) {'
new_guard = ('        // ⭐ The guard is our OWN fact, not another component\'s: lastUltEnergySpent is set only on the ultimate' + NL
             + '        // path and cleared right after it settles, so "> 0" means exactly "this hit is that ultimate".' + NL
             + '        // ⚠ An earlier version asked damage.getCastCategory() == ULTRA instead, and measured, that does NOT' + NL
             + '        // hold on the castImmediate path -- the write was skipped and the clause silently read 0.' + NL
             + '        if (lastUltEnergySpent > 0) {')
print('guard anchor: %d' % t.count(old_guard))
if t.count(old_guard) != 1:
    print('REFUSING: the guard anchor is not unique (or missing)')
    sys.exit(1)
t = t.replace(old_guard, new_guard, 1)

old_after = '        applyEnergyGain(user, ultraGain);   // then the 5 points of its own (× energy gain rate)'
new_after = (old_after + NL
             + '        lastUltEnergySpent = 0;             // ⚠ cleared, so the instance write cannot leak into later hits')
print('clear anchor: %d' % t.count(old_after))
if t.count(old_after) != 1:
    print('REFUSING: the clear anchor is not unique (or missing)')
    sys.exit(1)
t = t.replace(old_after, new_after, 1)
io.open(WORK + '/' + BATTLE, 'w', encoding='utf-8', newline='').write(t)
print('two edits applied')


def focused():
    for f in glob.glob(WORK + r'\build\test-results\test\*Cone23062EnergyTest*.xml'):
        os.remove(f)
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.Cone23062EnergyTest',
                        '--rerun-tasks', '--console=plain'], cwd=WORK, capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    outs, reds = [], []
    for f in glob.glob(WORK + r'\build\test-results\test\*Cone23062EnergyTest*.xml'):
        try:
            root = ET.parse(f).getroot()
        except Exception:
            continue
        for el in root.iter():
            for ch in (el.text, el.tail):
                for ln in (ch or '').split(NL):
                    if ln.strip().startswith('[23062]'):
                        outs.append(ln.strip())
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    reds.append((c.get('name'), (m.get('message') or '')[:220]))
    return r.returncode, reds, outs, ((r.stdout or '') + (r.stderr or ''))


code, reds, outs, out = focused()
print('focused exit %d ; reds=%s' % (code, [r[0] for r in reds]))
for o in outs[:2]:
    print('  OUT %s' % o[:200])
if reds:
    for n, m in reds[:2]:
        print('  FAIL %s: %s' % (n, m))
for l in out.split(NL):
    if '.java:' in l or '错误:' in l:
        print('  DIAG ' + l.strip()[:170])
