# -*- coding: utf-8 -*-
"""Round 928: ADD_ELEMENTAL_WEAKNESS gains two named element sources, and the loader gets a closed set to match."""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
NL = chr(10)
t = io.open(WORK + '/' + ENG, encoding='utf-8').read()

# ---- 1) the closed set, next to WIRED -------------------------------------------------
set_anchor = '    private static final Set<String> WIRED = Set.of('
special = (
    '    /**' + NL
    + '     * ⭐ The two element sources `ADD_ELEMENTAL_WEAKNESS` accepts besides a real element name' + NL
    + '     * (2026-09-30). ⚠ A closed set on purpose: without it a misspelled element would only blow up at' + NL
    + '     * RUN time, and only if the rule ever fired -- a wrong answer that reports nothing.' + NL
    + '     *' + NL
    + '     * <p>`party_first` -- 「场上我方目标持有属性的弱点」 (character 1006); its' + NL
    + '     * skill text names the rule: 「优先添加我方编队第一位角色持有属性的弱点」.' + NL
    + '     * <p>`random_absent` -- 「添加 1 个随机属性弱点，优先添加目标尚未拥有的弱点」 (character 1405).' + NL
    + '     */' + NL
    + '    private static final Set<String> SPECIAL_ELEMENTS = Set.of("party_first", "random_absent");' + NL + NL
    + set_anchor)
print('set anchor: %d' % t.count(set_anchor))
if t.count(set_anchor) != 1:
    print('REFUSING: the WIRED anchor is not unique')
    sys.exit(1)
t = t.replace(set_anchor, special, 1)

# ---- 2) the load-time check ------------------------------------------------------------
load_old = ('                if (effect.getElement() == null || effect.getElement().isBlank()) {' + NL
            + '                    throw new IllegalArgumentException("Op ADD_ELEMENTAL_WEAKNESS requires element (source: " + spec.getSource() + ")");' + NL
            + '                }')
load_new = ('                if (effect.getElement() == null || effect.getElement().isBlank()) {' + NL
            + '                    throw new IllegalArgumentException("Op ADD_ELEMENTAL_WEAKNESS requires element (source: " + spec.getSource() + ")");' + NL
            + '                }' + NL
            + '                // ⚠ Reject it HERE, not at run time: an unknown element that only blows up when the rule' + NL
            + '                // finally fires is exactly the silent mistake this library keeps closing.' + NL
            + '                String named = effect.getElement().trim();' + NL
            + '                if (!SPECIAL_ELEMENTS.contains(named)' + NL
            + '                        && com.laosun.aluminium.enums.DamageElement.fromString(named) == null) {' + NL
            + '                    throw new IllegalArgumentException("Op ADD_ELEMENTAL_WEAKNESS names element \\"" + named' + NL
            + '                            + "\\", which is neither a DamageElement nor one of " + SPECIAL_ELEMENTS' + NL
            + '                            + " (source: " + spec.getSource() + ")");' + NL
            + '                }')
print('load anchor: %d' % t.count(load_old))
if t.count(load_old) != 1:
    print('REFUSING: the loader anchor is not unique')
    sys.exit(1)
t = t.replace(load_old, load_new, 1)

# ---- 3) the exec-time resolution -------------------------------------------------------
exec_old = ('                DamageElement weakness = DamageElement.fromString(effect.getElement());' + NL
            + '                if (weakness == null) {' + NL
            + '                    throw new IllegalStateException("Op ADD_ELEMENTAL_WEAKNESS names an element that is not a DamageElement");' + NL
            + '                }')
exec_new = ('                // ⚠ Two named sources beside a real element name (the loader keeps this a closed set):' + NL
            + '                //   `party_first` -- the first character of the party, per 1006’s own skill text.' + NL
            + '                //   `random_absent` -- a random element the target does NOT already have (1405).' + NL
            + '                String named = effect.getElement().trim();' + NL
            + '                DamageElement weakness;' + NL
            + '                if ("party_first".equals(named)) {' + NL
            + '                    weakness = battle == null || battle.characters.isEmpty()' + NL
            + '                            ? null : battle.characters.getFirst().getElement();' + NL
            + '                } else if ("random_absent".equals(named)) {' + NL
            + '                    weakness = null;' + NL
            + '                    for (CanHit victim : resolveTargets(battle, effect, ctx)) {' + NL
            + '                        if (victim instanceof com.laosun.aluminium.models.enemy.Enemy en) {' + NL
            + '                            java.util.List<DamageElement> absent = new java.util.ArrayList<>();' + NL
            + '                            for (DamageElement candidate : DamageElement.values()) {' + NL
            + '                                if (!en.isWeakTo(candidate)) { absent.add(candidate); }' + NL
            + '                            }' + NL
            + '                            weakness = absent.isEmpty() ? null' + NL
            + '                                    : absent.get(battle.getRng().nextInt(absent.size()));' + NL
            + '                        }' + NL
            + '                    }' + NL
            + '                } else {' + NL
            + '                    weakness = DamageElement.fromString(named);' + NL
            + '                }' + NL
            + '                if (weakness == null) {' + NL
            + '                    // ⚠ Not an error for the two named sources: 1006 may have no party attribute to offer and' + NL
            + '                    // 1405 may find no absent element. The clause simply does nothing then.' + NL
            + '                    if (SPECIAL_ELEMENTS.contains(named)) { break; }' + NL
            + '                    throw new IllegalStateException("Op ADD_ELEMENTAL_WEAKNESS names an element that is not a DamageElement");' + NL
            + '                }')
print('exec anchor: %d' % t.count(exec_old))
if t.count(exec_old) != 1:
    print('REFUSING: the exec anchor is not unique')
    sys.exit(1)
t = t.replace(exec_old, exec_new, 1)
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
                    print('  FAIL %s: %s' % (c.get('name'), (m.get('message') or '')[:280]))
    for l in ((s.stdout or '') + (s.stderr or '')).split(NL):
        if '.java:' in l or 'error:' in l or '错误' in l:
            print('  DIAG ' + l.strip()[:180])
    print('REFUSING to commit')
    sys.exit(1)
print('gates %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', ENG], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: ADD_ELEMENTAL_WEAKNESS takes party_first and random_absent, validated at load time'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
