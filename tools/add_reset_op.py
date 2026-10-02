# -*- coding: utf-8 -*-
"""Round 731: the event-driven reset -- RESET_TRIGGER_LIMIT. First half of the count-and-reset family's engine.

CanHit already tracks three per-rule limit maps (triggerTurnUses / triggerSpentOnce / triggerCooldowns) and clears all
of them only at battle start (resetTriggerLimits). The three registered clauses reset ONE of their rules on ULT_CAST,
so clearing everything would disturb unrelated rules on the same event.
"""

import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CANHIT = 'src/main/java/com/laosun/aluminium/models/CanHit.java'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
NL = chr(10)
TOUCHED = [CANHIT, ENG]
saved = {p: io.open(WORK + '/' + p, encoding='utf-8').read() for p in TOUCHED}


def bail(msg):
    subprocess.run(['git', 'checkout', '--'] + TOUCHED, cwd=WORK)
    print('ROLLED BACK via git checkout (%s)' % msg)
    sys.exit(1)


# 1) CanHit: clear ONE rule's limits
old_canhit = '    public void tickTriggerCooldowns() {'
new_canhit = ('    /**' + NL
              + '     * Clears the firing limits of ONE rule (\u300c\u65bd\u653e\u7ec8\u7ed3\u6280\u540e\u91cd\u7f6e\u8be5\u6548\u679c\u89e6\u53d1\u6b21\u6570\u300d, 2026-09-30;' + NL
              + '     * readers 1305, 1207, 1403). ⚠ Deliberately not {@link #resetTriggerLimits()}: that clears EVERY rule of' + NL
              + '     * this combatant, and ULT_CAST carries unrelated rules too.' + NL
              + '     */' + NL
              + '    public void resetTriggerLimit(String key) {' + NL
              + '        triggerTurnUses.remove(key);' + NL
              + '        triggerSpentOnce.remove(key);' + NL
              + '        triggerCooldowns.remove(key);' + NL
              + '    }' + NL + NL
              + old_canhit)
if saved[CANHIT].count(old_canhit) != 1:
    bail('the CanHit anchor is not unique')
saved[CANHIT] = saved[CANHIT].replace(old_canhit, new_canhit, 1)

# 2) WIRED
old_wired = 'private static final Set<String> WIRED = Set.of('
if saved[ENG].count(old_wired) != 1:
    bail('the WIRED anchor is not unique')
saved[ENG] = saved[ENG].replace(old_wired, old_wired + NL + '            "RESET_TRIGGER_LIMIT",', 1)

# 3) the load-time case
old_load = ('            case "GAIN_ENERGY" -> {' + NL
            + '                requireAmountOrScale(effect, op, spec, ENERGY_SCALES, "max energy");')
new_load = ('            case "RESET_TRIGGER_LIMIT" -> {' + NL
            + '                if (effect.getRule() == null || effect.getRule().isBlank()) {' + NL
            + '                    throw new IllegalArgumentException(' + NL
            + '                            "Op RESET_TRIGGER_LIMIT requires \\"rule\\" (source: " + spec.getSource() + ")");' + NL
            + '                }' + NL
            + '                requireNoStackArguments(effect, op, spec);' + NL
            + '            }' + NL + old_load)
if saved[ENG].count(old_load) != 1:
    bail('the load anchor is not unique')
saved[ENG] = saved[ENG].replace(old_load, new_load, 1)

# 4) the execution case
old_exec = '            case "GAIN_ENERGY" -> gainEnergy(battle, effect, ctx);'
new_exec = ('            case "RESET_TRIGGER_LIMIT" -> {' + NL
            + '                for (CanHit owner : resolveTargets(battle, effect, ctx)) {' + NL
            + '                    owner.resetTriggerLimit(effect.getRule().trim());' + NL
            + '                }' + NL
            + '            }' + NL + old_exec)
if saved[ENG].count(old_exec) != 1:
    bail('the exec anchor is not unique')
saved[ENG] = saved[ENG].replace(old_exec, new_exec, 1)

for p, txt in saved.items():
    io.open(WORK + '/' + p, 'w', encoding='utf-8', newline='').write(txt)
print('four edits applied')


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
                      'feat: RESET_TRIGGER_LIMIT -- one rule limit cleared by an event, three registered clauses'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
