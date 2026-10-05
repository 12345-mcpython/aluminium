# -*- coding: utf-8 -*-
"""Round 802: Damage carries the energy its cast spent, on the same route as extraDefenceIgnore.

DEALING_DAMAGE is the only event that hands over the damage instance, so a value the settlement needs must ride on it
(the existing fields say so in their own javadoc). This half is inert until something reads it.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
DMG = 'src/main/java/com/laosun/aluminium/models/Damage.java'
NL = chr(10)

t = io.open(WORK + '/' + DMG, encoding='utf-8').read()
anchor = '    /** Extra DEF-ignore carried by THIS hit, added by a rule firing on {@code DEALING_DAMAGE} (2026-09-29). */'
new = ('    /**' + NL
       + '     * The energy the cast that produced THIS hit spent, or {@code 0} (2026-09-30; reader: light cone 23062).' + NL
       + '     *' + NL
       + '     * <p>⚠ It rides on the instance for the same reason the extras below do: 「每消耗 1 点能量值」' + NL
       + '     * modifies the hit BEING SETTLED, and {@code DEALING_DAMAGE} is the only event that hands that instance over.' + NL
       + '     * ⚠ Not a constructor parameter like {@code castCategory}: its value is only known after the damage is built' + NL
       + '     * (Battle reads the energy just before zeroing it) and before the settlement.' + NL
       + '     */' + NL
       + '    private double castEnergySpent = 0;' + NL + NL
       + anchor)
print('Damage anchor: %d' % t.count(anchor))
if t.count(anchor) != 1:
    print('REFUSING: the Damage anchor is not unique (or missing)')
    sys.exit(1)
t = t.replace(anchor, new, 1)

setter_anchor = '    /** 「对陷入负面效果的敌方目标造成伤害时暴击率提高 X%」 is a property of the hit, not of the wearer. */'
setter = ('    /** Records how much energy the producing cast spent; the settlement reads it (light cone 23062). */' + NL
          + '    public Damage withCastEnergySpent(double value) {' + NL
          + '        this.castEnergySpent = value;' + NL
          + '        return this;' + NL
          + '    }' + NL + NL
          + '    /** The energy the producing cast spent ({@code 0} for anything that is not an ultimate). */' + NL
          + '    public double getCastEnergySpent() {' + NL
          + '        return castEnergySpent;' + NL
          + '    }' + NL + NL
          + setter_anchor)
print('setter anchor: %d' % t.count(setter_anchor))
if t.count(setter_anchor) != 1:
    print('REFUSING: the setter anchor is not unique (or missing)')
    sys.exit(1)
t = t.replace(setter_anchor, setter, 1)
io.open(WORK + '/' + DMG, 'w', encoding='utf-8', newline='').write(t)
print('two edits applied')


def bail(msg):
    subprocess.run(['git', 'checkout', '--', DMG], cwd=WORK)
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
                      'feat: Damage carries the energy its cast spent, for DEALING_DAMAGE rules to scale on'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
