# -*- coding: utf-8 -*-
"""Round 757: TriggerSpec gains per_subject, and the CompiledRule record signature is printed for the next step.

The record's new component must be APPENDED: its parameters include several Strings and ints, so inserting one in the
middle would silently shift the others -- the compiler cannot catch that.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
SPEC = 'src/main/java/com/laosun/aluminium/beans/TriggerSpec.java'
TABLE = 'src/main/java/com/laosun/aluminium/models/TriggerTable.java'
NL = chr(10)

spec = io.open(WORK + '/' + SPEC, encoding='utf-8').read()
old = '    private Integer perTurn;'
new = ('    private Integer perTurn;' + NL + NL
       + '    /**' + NL
       + '     * Whose firings {@link #perTurn} and {@link #cooldown} count, when it is not the rule owner \u2014'
       + ' the count-and-reset' + NL
       + '     * family (2026-09-30; readers 1305, 1207, 1403) counts on the marked TARGET, on the TRIGGERER, or per UNIT.' + NL
       + '     * One of {@code self} / {@code target} / {@code actor}; absent means the owner, which is what every' + NL
       + '     * shipped rule already does, so omitting it changes nothing.' + NL
       + '     */' + NL
       + '    @SerializedName("per_subject")' + NL
       + '    private String perSubject;')
print('TriggerSpec anchor: %d' % spec.count(old))
if spec.count(old) != 1:
    print('REFUSING: the TriggerSpec anchor is not unique')
    sys.exit(1)
io.open(WORK + '/' + SPEC, 'w', encoding='utf-8', newline='').write(spec.replace(old, new, 1))
print('TriggerSpec.perSubject added')

for i, l in enumerate(io.open(WORK + '/' + TABLE, encoding='utf-8').read().split(NL)):
    if 'record CompiledRule' in l or (1795 < i < 1830 and l.strip().startswith('List<EffectSpec>')):
        print('%5d| %s' % (i + 1, l[:150]))
    if 1795 < i < 1830 and (l.strip().endswith(') {') or l.strip().endswith('{') or l.strip() == ')'):
        print('%5d| %s' % (i + 1, l[:150]))
        break


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
                                               (m.get('message') or '')[:200]))
    subprocess.run(['git', 'checkout', '--', SPEC], cwd=WORK)
    print('ROLLED BACK')
    sys.exit(1)
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: TriggerSpec gains per_subject for counts that belong to another unit'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
