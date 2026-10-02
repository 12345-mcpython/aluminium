# -*- coding: utf-8 -*-
"""Round 875: random_ally_below_half_energy -- name, single-target case, and the method (FILTER FIRST, ROLL SECOND).

Only three of the five landed points are needed for 21021: it restores energy to ONE ally, so the list side is not
reached (and the `default ->` branch would report it loudly if it were). The 50% threshold is written into the method
and registered rather than parameterised: the text states it once and no tier changes it.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
NL = chr(10)

t = io.open(WORK + '/' + ENG, encoding='utf-8').read()

name_anchor = '            "random_enemy", "random_hit_enemy",'
name_new = ('            "random_enemy", "random_hit_enemy",' + NL
            + '            // \u2b50 \u300c\u968f\u673a\u4e3a 1 \u4e2a\u5f53\u524d\u80fd\u91cf\u767e\u5206\u6bd4\u5c0f\u4e8e 50% \u7684\u6211\u65b9\u5176\u4ed6\u76ee\u6807\u300d (light cone 21021). \u26a0 The 50%'
            + NL
            + '            // threshold is the text\u2019s own and no tier changes it, so it is in the method and registered there.' + NL
            + '            "random_ally_below_half_energy",')
print('name anchor: %d' % t.count(name_anchor))
if t.count(name_anchor) != 1:
    print('REFUSING: the selector-list anchor is not unique')
    sys.exit(1)
t = t.replace(name_anchor, name_new, 1)

case_anchor = '            case "party_first" -> require(partyFirst(ctx), "party_first", ctx);'
case_new = (case_anchor + NL
            + '            case "random_ally_below_half_energy" ->' + NL
            + '                    require(randomAllyBelowHalfEnergy(ctx), "random_ally_below_half_energy", ctx);')
print('case anchor: %d' % t.count(case_anchor))
if t.count(case_anchor) != 1:
    print('REFUSING: the case anchor is not unique')
    sys.exit(1)
t = t.replace(case_anchor, case_new, 1)

method_anchor = '    /** The first character of the party (relic 317), or null when there is no battle. */'
method_new = ('    /**' + NL
              + '     * \u300c\u968f\u673a\u4e3a 1 \u4e2a\u5f53\u524d\u80fd\u91cf\u767e\u5206\u6bd4\u5c0f\u4e8e 50% \u7684\u6211\u65b9\u5176\u4ed6\u76ee\u6807\u300d (light cone 21021).' + NL
              + '     *' + NL
              + '     * <p>\u26a0 FILTER FIRST, ROLL SECOND \u2014 the same order `randomHitEnemy` documents: a roll landing on an' + NL
              + '     * excluded ally would be dropped rather than re-rolled, which is a wrong answer that reports nothing.' + NL
              + '     * \u26a0 The 50% is written in rather than carried by a field: the text states it once and no tier changes' + NL
              + '     * it (the five tiers differ only in how much energy is restored).' + NL
              + '     */' + NL
              + '    private static CanHit randomAllyBelowHalfEnergy(TriggerContext ctx) {' + NL
              + '        Battle battle = ctx.battle();' + NL
              + '        if (battle == null) {' + NL
              + '            return null;' + NL
              + '        }' + NL
              + '        List<CanHit> eligible = new java.util.ArrayList<>();' + NL
              + '        for (CanHit ally : battle.allies) {' + NL
              + '            if (ally == ctx.owner() || !(ally instanceof Character character)) {' + NL
              + '                continue;' + NL
              + '            }' + NL
              + '            double max = character.getMaxEnergy();' + NL
              + '            if (max > 0 && character.getCurrentEnergy() / max < 0.5) {' + NL
              + '                eligible.add(character);' + NL
              + '            }' + NL
              + '        }' + NL
              + '        return eligible.isEmpty() ? null' + NL
              + '                : eligible.get(battle.getRng().nextInt(eligible.size()));' + NL
              + '    }' + NL + NL
              + method_anchor)
print('method anchor: %d' % t.count(method_anchor))
if t.count(method_anchor) != 1:
    print('REFUSING: the method anchor is not unique')
    sys.exit(1)
t = t.replace(method_anchor, method_new, 1)
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(t)
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
                                               (m.get('message') or '')[:250]))
    for l in ((s.stdout or '') + (s.stderr or '')).split(NL):
        if '.java:' in l or '错误:' in l:
            print('  DIAG ' + l.strip()[:180])
    subprocess.run(['git', 'checkout', '--', ENG], cwd=WORK)
    print('ROLLED BACK')
    sys.exit(1)
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', ENG], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: a random_ally_below_half_energy selector, filter first and roll second'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
