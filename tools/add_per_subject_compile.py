# -*- coding: utf-8 -*-
"""Round 758: CompiledRule carries perSubject, and the loader validates it.

Appended at the END of the record: its parameters include adjacent ints and Strings, so an inserted component would
shift them silently.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
TABLE = 'src/main/java/com/laosun/aluminium/models/TriggerTable.java'
NL = chr(10)
saved = io.open(WORK + '/' + TABLE, encoding='utf-8').read()


def bail(msg):
    subprocess.run(['git', 'checkout', '--', TABLE], cwd=WORK)
    print('ROLLED BACK via git checkout (%s)' % msg)
    sys.exit(1)


# 1) the record gains a trailing component
old_rec = 'int perTurn, int perAttack, List<List<Condition>> effectTargetFilters) {'
new_rec = 'int perTurn, int perAttack, List<List<Condition>> effectTargetFilters, String perSubject) {'
if saved.count(old_rec) != 1:
    bail('the record anchor is not unique')
t = saved.replace(old_rec, new_rec, 1)

# 2) compile passes it
old_arg = 'validateMinEidolon(spec), validatePerTurn(spec), validatePerAttack(spec), List.copyOf(targetFilters)));'
new_arg = ('validateMinEidolon(spec), validatePerTurn(spec), validatePerAttack(spec), List.copyOf(targetFilters),'
           + NL + '                validatePerSubject(spec)));')
if t.count(old_arg) != 1:
    bail('the construction anchor is not unique')
t = t.replace(old_arg, new_arg, 1)

# 3) and it is validated, beside validateId
old_val = '    private static String validateId(TriggerSpec spec) {'
new_val = ('    /**' + NL
           + '     * Validates {@code per_subject} and returns it ({@code ""} = the rule owner, the shipped default).' + NL
           + '     *' + NL
           + '     * <p>Validated at load time like the other limits: an unknown name would fall back to the owner and' + NL
           + '     * count the wrong thing -- a wrong answer with no symptom, and the reason the family needs the field' + NL
           + '     * at all (2026-09-30; readers 1305, 1207, 1403).' + NL
           + '     */' + NL
           + '    private static String validatePerSubject(TriggerSpec spec) {' + NL
           + '        String subject = spec.getPerSubject();' + NL
           + '        if (subject == null || subject.isBlank()) {' + NL
           + '            return "";' + NL
           + '        }' + NL
           + '        String trimmed = subject.trim();' + NL
           + '        if (!SUBJECTS.contains(trimmed)) {' + NL
           + '            throw new IllegalArgumentException(' + NL
           + '                    "Trigger rule has \\"per_subject\\": " + trimmed + ", which is not one of " + SUBJECTS' + NL
           + '                            + "; omit the field to count the rule owner (source: " + spec.getSource() + ")");' + NL
           + '        }' + NL
           + '        return trimmed;' + NL
           + '    }' + NL + NL
           + old_val)
if t.count(old_val) != 1:
    bail('the validateId anchor is not unique')
t = t.replace(old_val, new_val, 1)

# 4) the closed set
old_wired = 'private static final Map<TriggerEvent, List<CompiledRule>> byEvent = new HashMap<>();'
new_wired = (old_wired + NL + NL
             + '    /** Who a per-turn or cooldown count may belong to when it is not the rule owner. */' + NL
             + '    private static final java.util.Set<String> SUBJECTS = java.util.Set.of("self", "target", "actor");')
if t.count(old_wired) != 1:
    bail('the byEvent anchor is not unique')
t = t.replace(old_wired, new_wired, 1)

io.open(WORK + '/' + TABLE, 'w', encoding='utf-8', newline='').write(t)
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
                      'feat: CompiledRule carries perSubject, validated at load time'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
