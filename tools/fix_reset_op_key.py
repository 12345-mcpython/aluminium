# -*- coding: utf-8 -*-
"""Round 739: RESET_TRIGGER_LIMIT resolves an id to the rule's real limiter key, and refuses an unknown id.

Round 734 measured `got 1`: the op passed the rule id where the limiter wanted `CompiledRule.key()`, so the clear was a
silent no-op. CompiledRule carries both (TriggerTable:1791-1792) and `key` exists because `id` is optional -- several
shipped rules have none (1302 has seven).
"""

import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
TABLE = 'src/main/java/com/laosun/aluminium/models/TriggerTable.java'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
NL = chr(10)
TOUCHED = [TABLE, ENG]
saved = {p: io.open(WORK + '/' + p, encoding='utf-8').read() for p in TOUCHED}


def bail(msg):
    subprocess.run(['git', 'checkout', '--'] + TOUCHED, cwd=WORK)
    print('ROLLED BACK via git checkout (%s)' % msg)
    sys.exit(1)


# 1) TriggerTable.keyOf: the limiter key behind an id
old_t = '    public List<CompiledRule> rulesFor(TriggerEvent event) {'
new_t = ('    /**' + NL
         + '     * The limiter key of the rule carrying this id, or {@code null} when this table has no such id.' + NL
         + '     *' + NL
         + '     * <p>⚠ An id is optional and a key is not -- that is why both exist (see {@link CompiledRule}), and why' + NL
         + '     * a caller holding an id cannot clear a limit without this lookup. Seven shipped rules have no id at all.' + NL
         + '     */' + NL
         + '    public String keyOf(String id) {' + NL
         + '        if (id == null) {' + NL
         + '            return null;' + NL
         + '        }' + NL
         + '        for (List<CompiledRule> rules : byEvent.values()) {' + NL
         + '            for (CompiledRule rule : rules) {' + NL
         + '                if (id.equals(rule.id())) {' + NL
         + '                    return rule.key();' + NL
         + '                }' + NL
         + '            }' + NL
         + '        }' + NL
         + '        return null;' + NL
         + '    }' + NL + NL
         + old_t)
if saved[TABLE].count(old_t) != 1:
    bail('the TriggerTable anchor is not unique')
saved[TABLE] = saved[TABLE].replace(old_t, new_t, 1)

# 2) the op resolves the id and refuses an unknown one
old_case = ('            case "RESET_TRIGGER_LIMIT" -> {' + NL
            + '                for (CanHit owner : resolveTargets(battle, effect, ctx)) {' + NL
            + '                    owner.resetTriggerLimit(effect.getRule().trim());' + NL
            + '                }' + NL
            + '            }')
new_case = ('            case "RESET_TRIGGER_LIMIT" -> {' + NL
            + '                String wanted = effect.getRule().trim();' + NL
            + '                for (CanHit cleared : resolveTargets(battle, effect, ctx)) {' + NL
            + '                    TriggerTable table = cleared instanceof com.laosun.aluminium.models.Character ch' + NL
            + '                            ? ch.getTriggerTable() : null;' + NL
            + '                    String limitKey = table == null ? null : table.keyOf(wanted);' + NL
            + '                    if (limitKey == null) {' + NL
            + '                        throw new IllegalStateException(' + NL
            + '                                "Op RESET_TRIGGER_LIMIT names the rule \\"wanted\\", which " + cleared.getName()' + NL
            + '                                        + " does not carry -- an id that matches nothing would clear nothing, silently");' + NL
            + '                    }' + NL
            + '                    cleared.resetTriggerLimit(limitKey);' + NL
            + '                }' + NL
            + '            }')
new_case = new_case.replace('\\"wanted\\"', '" + wanted + "')
if saved[ENG].count(old_case) != 1:
    bail('the op anchor is not unique')
saved[ENG] = saved[ENG].replace(old_case, new_case, 1)

for p, txt in saved.items():
    io.open(WORK + '/' + p, 'w', encoding='utf-8', newline='').write(txt)
print('two edits applied')


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
                      'feat: RESET_TRIGGER_LIMIT resolves an id to its limiter key, and refuses an unknown one'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
