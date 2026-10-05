# -*- coding: utf-8 -*-
"""Round 838: BOOST_DAMAGE also honours the ceiling, then the mutation proves the judge is not blind.

applyDerivedCeiling is the wrapper MODIFY_ATTR's instance route ends with (TriggerInterpreter:2719-2721); copying only
derivedMagnitude left `cap_amount` unread -- the fourth "loader accepts, method ignores" accident in this file.
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
JREL = 'src/test/java/com/laosun/aluminium/test/Cone23062EnergyTest.java'
NL = chr(10)

t = io.open(WORK + '/' + ENG, encoding='utf-8').read()
anchor = '        damage.addBoost(magnitude);'
new = ('        // ⭐ The ceiling too, and through the same wrapper MODIFY_ATTR\'s instance route ends with: copying only' + NL
       + '        // derivedMagnitude left `cap_amount` unread (measured: 1,000,000 points gave 16498 instead of the cap).' + NL
       + '        damage.addBoost(applyDerivedCeiling(effect, ctx, magnitude));')
print('anchor: %d' % t.count(anchor))
if t.count(anchor) != 1:
    print('REFUSING: the addBoost anchor is not unique (or missing)')
    sys.exit(1)
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(t.replace(anchor, new, 1))
saved = io.open(WORK + '/' + ENG, encoding='utf-8').read()
print('one edit applied')


def bail(msg):
    if os.path.exists(WORK + '/' + JREL):
        os.remove(WORK + '/' + JREL)
    subprocess.run(['git', 'checkout', '--', ENG], cwd=WORK)
    print('ROLLED BACK (%s)' % msg)
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
                    reds.append((c.get('name'), (m.get('message') or '')[:200]))
    return r.returncode, reds, outs, ((r.stdout or '') + (r.stderr or ''))


code, reds, outs, out = focused()
print('focused exit %d ; reds=%s' % (code, [r[0] for r in reds]))
for o in outs[:3]:
    print('  OUT %s' % o[:210])
if reds:
    for n, m in reds[:2]:
        print('  FAIL %s: %s' % (n, m))
    for l in out.split(NL):
        if '.java:' in l:
            print('  DIAG ' + l.strip()[:170])
    bail('the judge is red')

mut_old = '            damage.withCastEnergySpent(lastUltEnergySpent);'
print('mutation anchor: %d' % saved.count(mut_old))
if saved.count(mut_old) != 1:
    bail('the mutation anchor is not unique')
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(
    saved.replace(mut_old, '            // MUTATION: the spend never reaches the instance', 1))
_, r2, _, _, _ = focused()
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(saved)
print('mutation (spend not carried) reds=%s -> %s' % (len(r2), 'red' if r2 else 'GREEN (blind!)'))
if not r2:
    bail('the mutation is invisible')
s = subprocess.run([r'.\gradlew.bat', 'test', '--rerun-tasks', '--quiet', '--console=plain'], cwd=WORK,
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
print('suite %d' % s.returncode)
if s.returncode != 0:
    bail('suite red')
g = [subprocess.run([r'.\gradlew.bat', 'run', *a, '--quiet', '--console=plain'], cwd=WORK,
                    capture_output=True, text=True).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: BOOST_DAMAGE reads scale and the ceiling, so an energy-scaled clause works'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
