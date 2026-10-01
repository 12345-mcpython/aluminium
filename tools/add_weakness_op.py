# -*- coding: utf-8 -*-
"""Round 718: the weakness-adding op -- the engine half.

Enemy already holds `Set<DamageElement> stanceWeak` (Enemy:60) and both isWeakTo and weaknessCount read it, but nothing
can add to it after the factory built the unit (EnemyFactory:104 uses setStanceWeak). Enemy:192 asks callers not to
reach for the field directly, so the entry point belongs on Enemy.
"""

import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENEMY = 'src/main/java/com/laosun/aluminium/models/enemy/Enemy.java'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
NL = chr(10)
TOUCHED = [ENEMY, ENG]
saved = {p: io.open(WORK + '/' + p, encoding='utf-8').read() for p in TOUCHED}


def bail(msg):
    subprocess.run(['git', 'checkout', '--'] + TOUCHED, cwd=WORK)
    print('ROLLED BACK via git checkout (%s)' % msg)
    sys.exit(1)


# 1) Enemy: the entry point, right after isWeakTo
old_enemy = ('    public boolean isWeakTo(DamageElement element) {' + NL
             + '        return element != null && stanceWeak.contains(element);' + NL
             + '    }')
new_enemy = (old_enemy + NL + NL
             + '    /**' + NL
             + '     * Adds an element to this enemy\u2019s weaknesses (\u300c\u4e3a\u6307\u5b9a\u654c\u65b9\u5355\u4f53\u6dfb\u52a0\u7269\u7406\u5f31\u70b9\u300d, 2026-09-30).' + NL
             + '     *' + NL
             + '     * <p>Goes through the setter rather than the field, as the note above asks. ⚠ No LIFETIME: the shipped' + NL
             + '     * documents say \u300c\u6301\u7eed 2 \u56de\u5408\u300d, and a set has no expiry -- that half stays registered.' + NL
             + '     */' + NL
             + '    public void addWeakness(DamageElement element) {' + NL
             + '        if (element == null || stanceWeak.contains(element)) {' + NL
             + '            return;' + NL
             + '        }' + NL
             + '        java.util.Set<DamageElement> widened = new java.util.HashSet<>(stanceWeak);' + NL
             + '        widened.add(element);' + NL
             + '        setStanceWeak(widened);' + NL
             + '    }')
print('Enemy anchor: %d' % saved[ENEMY].count(old_enemy))
if saved[ENEMY].count(old_enemy) != 1:
    bail('the Enemy anchor is not unique')
saved[ENEMY] = saved[ENEMY].replace(old_enemy, new_enemy, 1)

# 2) WIRED gains the op name
old_wired = 'private static final Set<String> WIRED = Set.of('
new_wired = ('private static final Set<String> WIRED = Set.of(' + NL
             + '            // \u2b50 \u300c\u4e3a\u6307\u5b9a\u654c\u65b9\u5355\u4f53\u6dfb\u52a0 X \u5c5e\u6027\u5f31\u70b9\u300d (2026-09-30; readers 1315, 1310).' + NL
             + '            "ADD_ELEMENTAL_WEAKNESS",')
print('WIRED anchor: %d' % saved[ENG].count(old_wired))
if saved[ENG].count(old_wired) != 1:
    bail('the WIRED anchor is not unique')
saved[ENG] = saved[ENG].replace(old_wired, new_wired, 1)

# 3) the load-time case (before GAIN_ENERGY's)
old_load = '            case "GAIN_ENERGY" -> {' + NL + '                requireAmountOrScale(effect, op, spec, ENERGY_SCALES, "max energy");'
new_load = ('            case "ADD_ELEMENTAL_WEAKNESS" -> {' + NL
            + '                if (effect.getElement() == null || effect.getElement().isBlank()) {' + NL
            + '                    throw new IllegalArgumentException(' + NL
            + '                            "Op ADD_ELEMENTAL_WEAKNESS requires \\"element\\" (source: " + spec.getSource() + ")");' + NL
            + '                }' + NL
            + '                requireNoStackArguments(effect, op, spec);' + NL
            + '            }' + NL
            + old_load)
print('load anchor: %d' % saved[ENG].count(old_load))
if saved[ENG].count(old_load) != 1:
    bail('the load anchor is not unique')
saved[ENG] = saved[ENG].replace(old_load, new_load, 1)

# 4) the execution case (before GAIN_ENERGY's)
old_exec = '            case "GAIN_ENERGY" -> gainEnergy(battle, effect, ctx);'
new_exec = ('            case "ADD_ELEMENTAL_WEAKNESS" -> {' + NL
            + '                DamageElement weakness = DamageElement.fromString(effect.getElement());' + NL
            + '                if (weakness == null) {' + NL
            + '                    throw new IllegalStateException("Op ADD_ELEMENTAL_WEAKNESS names element " + effect.getElement() + ", which is not a DamageElement (source: " + spec.getSource() + ")");' + NL
            + '                }' + NL
            + '                for (CanHit victim : resolveTargets(battle, effect, ctx)) {' + NL
            + '                    if (victim instanceof com.laosun.aluminium.models.enemy.Enemy enemy) {' + NL
            + '                        enemy.addWeakness(weakness);' + NL
            + '                    }' + NL
            + '                }' + NL
            + '            }' + NL
            + old_exec)
print('exec anchor: %d' % saved[ENG].count(old_exec))
if saved[ENG].count(old_exec) != 1:
    bail('the exec anchor is not unique')
saved[ENG] = saved[ENG].replace(old_exec, new_exec, 1)

for p, txt in saved.items():
    io.open(WORK + '/' + p, 'w', encoding='utf-8', newline='').write(txt)
print('four edits written')


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
    for l in ((s.stdout or '') + (s.stderr or '')).strip().split(NL):
        if 'error:' in l or '错误' in l:
            print('  ERR ' + l.strip()[:200])
        print('  RAW ' + l.strip()[:160])
    bail('suite red')
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: ADD_ELEMENTAL_WEAKNESS -- two registered clauses need to add a weakness'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
