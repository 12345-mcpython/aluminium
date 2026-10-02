# -*- coding: utf-8 -*-
"""Round 805: Battle hands the ultimate's spent energy to the damage instance before DEALING_DAMAGE fires.

The guard is not decoration: `lastUltEnergySpent` is only written on the ultimate path and is never cleared, so without
`castCategory == ULTRA` every later hit (basic attacks, DOT ticks, break damage) would inherit it -- an error with no
symptom. The instance itself needs no cleanup, as the comment above the firing site says.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
BATTLE = 'src/main/java/com/laosun/aluminium/Battle.java'
NL = chr(10)

t = io.open(WORK + '/' + BATTLE, encoding='utf-8').read()
anchor = ('        fireTriggers(TriggerEvent.DEALING_DAMAGE, attacker, defender, 0, damage.getSkillBaseValue(), damage, damage.getCastCategory(),'
          + NL + '                damage.getSkillKey());')
new = ('        // \u2b50 \u300c\u6bcf\u6d88\u8017 1 \u70b9\u80fd\u91cf\u503c\u300d (light cone 23062) scales off THIS cast\u2019s spend, which is why it rides on the' + NL
       + '        // instance: the settlement reads the instance, and this is the only event that hands it over.' + NL
       + '        // \u26a0 The guard is load-bearing: lastUltEnergySpent is written only on the ultimate path and never' + NL
       + '        // cleared, so without it every later hit -- basics, DOT ticks, break damage -- would inherit it, an' + NL
       + '        // error with no symptom.' + NL
       + '        if (damage.getCastCategory() == com.laosun.aluminium.enums.SkillCategory.ULTRA) {' + NL
       + '            damage.withCastEnergySpent(lastUltEnergySpent);' + NL
       + '        }' + NL
       + anchor)
print('Battle anchor: %d' % t.count(anchor))
if t.count(anchor) != 1:
    print('REFUSING: the Battle anchor is not unique (or missing)')
    sys.exit(1)
io.open(WORK + '/' + BATTLE, 'w', encoding='utf-8', newline='').write(t.replace(anchor, new, 1))
print('one edit applied')


def bail(msg):
    subprocess.run(['git', 'checkout', '--', BATTLE], cwd=WORK)
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
    for l in ((s.stdout or '') + (s.stderr or '')).split(NL):
        if 'error:' in l or '错误' in l or '.java:' in l:
            print('  DIAG ' + l.strip()[:190])
    bail('suite red')
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: the ultimate\'s spent energy reaches DEALING_DAMAGE on the damage instance'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
