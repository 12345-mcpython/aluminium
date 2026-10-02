# -*- coding: utf-8 -*-
"""Round 788: ULT_CAST finally carries the energy the ultimate spent (it was hard-coded 0).

Battle zeroes the energy BEFORE the ultimate body settles (documented at :664-667: reversing it would eat the energy
the ultimate itself earns), so the amount is simply read just before `setCurrentEnergy(0)`. SkillExecutor fires the
event one layer down and cannot see it, hence the field.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
BATTLE = 'src/main/java/com/laosun/aluminium/Battle.java'
EXEC = 'src/main/java/com/laosun/aluminium/models/skill/SkillExecutor.java'
NL = chr(10)
TOUCHED = [BATTLE, EXEC]
saved = {p: io.open(WORK + '/' + p, encoding='utf-8').read() for p in TOUCHED}


def bail(msg):
    subprocess.run(['git', 'checkout', '--'] + TOUCHED, cwd=WORK)
    print('ROLLED BACK via git checkout (%s)' % msg)
    sys.exit(1)


# 1) the field, declared at class level just before a uniquely named method
anchor = '    private void registerPartyResources() {'
field = ('    /**' + NL
         + '     * The energy the ultimate now settling consumed, for {@code ULT_CAST}\u2019s amount.' + NL
         + '     *' + NL
         + '     * <p>\u26a0 It cannot be read one layer down: Battle zeroes the energy BEFORE the ultimate body settles' + NL
         + '     * (see the H-5 comment above), so by the time the event fires the unit already reads 0.' + NL
         + '     */' + NL
         + '    private double lastUltEnergySpent;' + NL + NL
         + '    /** The energy the ultimate now settling consumed ({@code 0} when none is in flight). */' + NL
         + '    public double getLastUltEnergySpent() {' + NL
         + '        return lastUltEnergySpent;' + NL
         + '    }' + NL + NL
         + anchor)
if saved[BATTLE].count(anchor) != 1:
    bail('the Battle anchor is not unique')
t = saved[BATTLE].replace(anchor, field, 1)

# 2) capture it before zeroing
old_zero = '        user.setCurrentEnergy(0);'
new_zero = ('        lastUltEnergySpent = user.getCurrentEnergy();   // \u2b50 read it BEFORE the zeroing below' + NL
            + old_zero)
if t.count(old_zero) != 1:
    bail('the zeroing anchor is not unique')
t = t.replace(old_zero, new_zero, 1)
io.open(WORK + '/' + BATTLE, 'w', encoding='utf-8', newline='').write(t)

# 3) the event carries it
old_fire = 'case ULTRA -> battle.fireTriggers(TriggerEvent.ULT_CAST, user, aimed, hits.size(), 0, category,'
new_fire = ('case ULTRA -> battle.fireTriggers(TriggerEvent.ULT_CAST, user, aimed, hits.size(),'
            ' battle.getLastUltEnergySpent(), category,')
e = saved[EXEC]
if e.count(old_fire) != 1:
    bail('the ULT_CAST anchor is not unique')
io.open(WORK + '/' + EXEC, 'w', encoding='utf-8', newline='').write(e.replace(old_fire, new_fire, 1))
print('three edits applied')


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
                      'feat: ULT_CAST carries the energy the ultimate spent, instead of a hard-coded zero'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
