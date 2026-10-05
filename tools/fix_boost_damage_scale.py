# -*- coding: utf-8 -*-
"""Round 830: BOOST_DAMAGE honours `scale` (it ignored it, exactly as it once ignored `damage_type`).

The comment above the call records that earlier accident and names the fix's semantics: "Same semantics as
MODIFY_ATTR's instance route" -- so a stated scale goes through derivedMagnitude, and a bare percent stays as it was.
"""

import glob
import io
import os
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
NL = chr(10)

t = io.open(WORK + '/' + ENG, encoding='utf-8').read()
anchor = '        damage.addBoost(effect.getPercent());'
new = ('        // ⭐ A stated `scale` must actually be read (2026-09-30; reader: light cone 23062). ⚠ This method' + NL
       + '        // once ignored `damage_type` the same way and the loader accepted it -- see the comment above, round 258.' + NL
       + '        // The same semantics the note names: MODIFY_ATTR\'s instance route, i.e. derivedMagnitude.' + NL
       + '        double magnitude = effect.getScale() == null || effect.getScale().isBlank()' + NL
       + '                ? effect.getPercent()' + NL
       + '                : derivedMagnitude(effect, ctx);' + NL
       + '        damage.addBoost(magnitude);')
print('anchor: %d' % t.count(anchor))
if t.count(anchor) != 1:
    print('REFUSING: the boostDamage anchor is not unique (or missing)')
    sys.exit(1)
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(t.replace(anchor, new, 1))
print('one edit applied')


def bail(msg):
    subprocess.run(['git', 'checkout', '--', ENG], cwd=WORK)
    print('ROLLED BACK via git checkout (%s)' % msg)
    sys.exit(1)


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
    print('  OUT %s' % o[:190])
if reds:
    for n, m in reds[:2]:
        print('  FAIL %s: %s' % (n, m))
for l in out.split(NL):
    if '.java:' in l or '错误:' in l:
        print('  DIAG ' + l.strip()[:170])
