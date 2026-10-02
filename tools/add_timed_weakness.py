# -*- coding: utf-8 -*-
"""Round 937: weaknesses that expire. A SECOND table beside the data one, so no existing reader changes meaning."""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENEMY = 'src/main/java/com/laosun/aluminium/models/enemy/Enemy.java'
BATTLE = 'src/main/java/com/laosun/aluminium/Battle.java'
INTERP = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
NL = chr(10)


def read(rel):
    return io.open(WORK + '/' + rel, encoding='utf-8').read()


def write(rel, text):
    io.open(WORK + '/' + rel, 'w', encoding='utf-8', newline='').write(text)


def sub(text, old, new, label):
    n = text.count(old)
    print('%s anchor: %d' % (label, n))
    if n != 1:
        print('REFUSING: %s anchor is not unique' % label)
        sys.exit(1)
    return text.replace(old, new, 1)


# ---- Enemy: the second table ---------------------------------------------------------
en = read(ENEMY)
en = sub(en, '    private Set<DamageElement> stanceWeak = Set.of();',
         '    private Set<DamageElement> stanceWeak = Set.of();' + NL + NL
         + '    /**' + NL
         + '     * \u2b50 Weaknesses an EFFECT inserted, which EXPIRE: element \u21d2 turns still to run (2026-09-30).' + NL
         + '     * \u26a0 A second table on purpose, not a replacement: {@link #stanceWeak} is DATA (from' + NL
         + '     * {@code monster_config.json}) and never expires, so every existing reader keeps its meaning.' + NL
         + '     * \u26a0 Counted in the TARGET\u2019s own turns, matching {@code TURN_END} where it is ticked.' + NL
         + '     */' + NL
         + '    private final java.util.Map<DamageElement, Integer> timedWeak = new java.util.LinkedHashMap<>();',
         'field')
en = sub(en, '        return element != null && stanceWeak.contains(element);',
         '        return element != null && (stanceWeak.contains(element) || timedWeak.containsKey(element));',
         'isWeakTo')
en = sub(en, '        java.util.Set<DamageElement> widened = new java.util.HashSet<>(stanceWeak);' + NL
             + '        widened.add(element);' + NL
             + '        setStanceWeak(widened);' + NL
             + '    }',
         '        java.util.Set<DamageElement> widened = new java.util.HashSet<>(stanceWeak);' + NL
         + '        widened.add(element);' + NL
         + '        setStanceWeak(widened);' + NL
         + '    }' + NL + NL
         + '    /**' + NL
         + '     * \u2b50 \u300c\u6dfb\u52a0\u2026\u5f31\u70b9\uff0c\u6301\u7eed N \u56de\u5408\u300d (1006 \u00b7 1405 \u00b7 1310 \u00b7 1315): the same insertion, but it expires.' + NL
         + '     * \u26a0 A repeat call REFRESHES the count rather than being ignored -- the text says the weakness lasts N' + NL
         + '     * turns, and re-inserting an existing one is still that clause firing (unlike {@code WEAKNESS_ADDED},\u2026)' + NL
         + '     * which the caller guards separately.)' + NL
         + '     */' + NL
         + '    public void addWeakness(DamageElement element, int turns) {' + NL
         + '        if (element == null || turns <= 0) {' + NL
         + '            return;' + NL
         + '        }' + NL
         + '        timedWeak.put(element, turns);' + NL
         + '    }' + NL + NL
         + '    /** One of the target\u2019s own turns has ended: run every timed weakness down, dropping the expired. */' + NL
         + '    public void tickTimedWeaknesses() {' + NL
         + '        if (timedWeak.isEmpty()) {' + NL
         + '            return;' + NL
         + '        }' + NL
         + '        java.util.Map<DamageElement, Integer> left = new java.util.LinkedHashMap<>();' + NL
         + '        for (java.util.Map.Entry<DamageElement, Integer> each : timedWeak.entrySet()) {' + NL
         + '            if (each.getValue() > 1) {' + NL
         + '                left.put(each.getKey(), each.getValue() - 1);' + NL
         + '            }' + NL
         + '        }' + NL
         + '        timedWeak.clear();' + NL
         + '        timedWeak.putAll(left);' + NL
         + '    }',
         'addWeakness')
en = sub(en, '        return stanceWeak.size();',
         '        java.util.Set<DamageElement> both = new java.util.HashSet<>(stanceWeak);' + NL
         + '        both.addAll(timedWeak.keySet());' + NL
         + '        return both.size();',
         'weaknessCount')
write(ENEMY, en)

# ---- Battle: tick on the actor's turn end ---------------------------------------------
ba = read(BATTLE)
ba = sub(ba, '            fireTriggers(TriggerEvent.TURN_END, actor, actor, 0, 0);',
         '            // \u2b50 A timed weakness is counted in the TARGET\u2019s own turns, so it runs down when that unit\u2019s turn ends.' + NL
         + '            if (actor instanceof com.laosun.aluminium.models.enemy.Enemy ticking) {' + NL
         + '                ticking.tickTimedWeaknesses();' + NL
         + '            }' + NL
         + '            fireTriggers(TriggerEvent.TURN_END, actor, actor, 0, 0);',
         'turn-end')
write(BATTLE, ba)

# ---- Interpreter: read `turns` --------------------------------------------------------
it = read(INTERP)
it = sub(it, '                        en.addWeakness(weakness);',
         '                        if (effect.getTurns() != null && effect.getTurns() > 0) {' + NL
         + '                            en.addWeakness(weakness, effect.getTurns());' + NL
         + '                        } else {' + NL
         + '                            en.addWeakness(weakness);' + NL
         + '                        }',
         'insertion')
write(INTERP, it)
print('four files edited')


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
                    print('  FAIL %s: %s' % (c.get('name'), (m.get('message') or '')[:280]))
    for l in ((s.stdout or '') + (s.stderr or '')).split(NL):
        if '.java:' in l or 'error:' in l or '\u9519\u8bef' in l:
            print('  DIAG ' + l.strip()[:180])
    print('REFUSING to commit')
    sys.exit(1)
print('gates %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', ENEMY, BATTLE, INTERP], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: a weakness can expire -- a second table ticked at the target turn end'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
