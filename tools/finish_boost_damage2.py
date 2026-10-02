# -*- coding: utf-8 -*-
"""Round 842: finish BOOST_DAMAGE (idempotently) and prove the judge with a mutation.

Both anchors are regex-based or presence-checked, because a script that runs twice must survive the traces its own
first run left behind -- an exact-text anchor broke on round 838 exactly that way (indentation moved when round 831
wrapped the write in an if).
"""

import glob
import io
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
TESTNAME = 'com.laosun.aluminium.test.Cone23062SpendTest'
NL = chr(10)

t = io.open(WORK + '/' + ENG, encoding='utf-8').read()
if 'damage.addBoost(applyDerivedCeiling(effect, ctx, magnitude))' in t:
    print('boostDamage: already wired, skipping')
else:
    anchor = '        damage.addBoost(magnitude);'
    if t.count(anchor) != 1:
        print('REFUSING: the addBoost anchor is not unique (or missing)')
        sys.exit(1)
    new = ('        // \u2b50 The ceiling too, through the same wrapper MODIFY_ATTR\'s instance route ends with: copying' + NL
           + '        // only derivedMagnitude left `cap_amount` unread (measured: 1,000,000 points gave 16498, not the cap).' + NL
           + '        damage.addBoost(applyDerivedCeiling(effect, ctx, magnitude));')
    io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(t.replace(anchor, new, 1))
    print('TriggerInterpreter: ceiling wired')
saved = io.open(WORK + '/' + ENG, encoding='utf-8').read()


def run_focused():
    for f in glob.glob(WORK + r'\build\test-results\test\*Cone23062SpendTest*.xml'):
        os.remove(f)
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', TESTNAME, '--rerun-tasks', '--console=plain'],
                       cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')
    outs, reds = [], []
    for f in glob.glob(WORK + r'\build\test-results\test\*Cone23062SpendTest*.xml'):
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


code, reds, outs, out = run_focused()
print('focused exit %d ; reds=%s' % (code, [r[0] for r in reds]))
for o in outs[:3]:
    print('  OUT %s' % o[:210])
if reds:
    for n, m in reds[:2]:
        print('  FAIL %s: %s' % (n, m))
    for l in out.split(NL):
        if '.java:' in l:
            print('  DIAG ' + l.strip()[:170])
    sys.exit(1)

mut_re = re.compile(r'\n[ \t]*damage\.withCastEnergySpent\(lastUltEnergySpent\);')
print('mutation matches: %d' % len(mut_re.findall(saved)))
if len(mut_re.findall(saved)) != 1:
    print('REFUSING: the mutation anchor is not unique')
    sys.exit(1)
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(
    mut_re.sub(NL + '            // MUTATION: the spend never reaches the instance', saved))
_, r2, _, _, _ = run_focused()
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(saved)
print('mutation reds=%d -> %s' % (len(r2), 'red (good)' if r2 else 'GREEN (BLIND!)'))
if not r2:
    sys.exit(1)
s = subprocess.run([r'.\gradlew.bat', 'test', '--rerun-tasks', '--quiet', '--console=plain'], cwd=WORK,
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
print('suite %d' % s.returncode)
if s.returncode != 0:
    subprocess.run(['git', 'checkout', '--', ENG], cwd=WORK)
    print('ROLLED BACK the engine only; the judge stays (it is evidence, not a half-done ability)')
    sys.exit(1)
g = [subprocess.run([r'.\gradlew.bat', 'run', *a, '--quiet', '--console=plain'], cwd=WORK,
                    capture_output=True, text=True).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: BOOST_DAMAGE reads scale and the ceiling, so the energy-scaled clause works'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
