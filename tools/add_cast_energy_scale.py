# -*- coding: utf-8 -*-
"""Round 808: the `cast_energy_spent` scale -- 「每消耗 1 点能量值」 read off the damage instance.

The branch MUST come before the attribute branch: my source is not an attribute, so scaleAttribute would throw
"this unit has no resolved value for X" -- an error that reads like missing unit data rather than a misplaced branch.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
NL = chr(10)

t = io.open(WORK + '/' + ENG, encoding='utf-8').read()

const_anchor = '    private static final String SELF_MAX_ENERGY = "self_max_energy";'
const_new = ('    private static final String SELF_MAX_ENERGY = "self_max_energy";' + NL + NL
             + '    /**' + NL
             + '     * 「每消耗 1 点能量值」 (light cone 23062): the points are the energy THE CAST ITSELF spent.' + NL
             + '     *' + NL
             + '     * <p>⚠ Not an attribute, so it cannot go through 算子 「scaleAttribute」 — its branch below returns first.' + NL
             + '     * The value rides on the damage instance because DEALING_DAMAGE is the only event that hands it over.' + NL
             + '     * ⚠ A missing instance and a non-ultimate both read 0, which is the right answer for both: this' + NL
             + '     * clause adds nothing when no energy was spent.' + NL
             + '     */' + NL
             + '    private static final String CAST_ENERGY_SPENT = "cast_energy_spent";')
print('const anchor: %d' % t.count(const_anchor))
if t.count(const_anchor) != 1:
    print('REFUSING: the constant anchor is not unique (or missing)')
    sys.exit(1)
t = t.replace(const_anchor, const_new, 1)

branch_anchor = '        if (SELF_MAX_ENERGY.equals(effect.getScale().trim())) {'
branch_new = ('        if (CAST_ENERGY_SPENT.equals(effect.getScale().trim())) {' + NL
              + '            // ⚠ Before the attribute branch below, because this source is not an attribute and' + NL
              + '            // scaleAttribute would throw a message that reads like missing unit data.' + NL
              + '            com.laosun.aluminium.models.Damage hit = ctx.damage();' + NL
              + '            double spent = hit == null ? 0 : hit.getCastEnergySpent();' + NL
              + '            return effect.getPercent() * spent + (effect.getAmount() == null ? 0 : effect.getAmount());' + NL
              + '        }' + NL
              + branch_anchor)
print('branch anchor: %d' % t.count(branch_anchor))
if t.count(branch_anchor) != 1:
    print('REFUSING: the branch anchor is not unique (or missing)')
    sys.exit(1)
t = t.replace(branch_anchor, branch_new, 1)
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(t)
print('two edits applied')


def bail(msg):
    subprocess.run(['git', 'checkout', '--', ENG], cwd=WORK)
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
g = [run('run', *a).returncode for a in ([], ['--mechanics '])]
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: a cast_energy_spent scale, read off the damage instance'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
