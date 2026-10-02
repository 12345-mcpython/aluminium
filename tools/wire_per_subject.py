# -*- coding: utf-8 -*-
"""Round 764: per-subject firing counts -- one expression, read at all four sites.

ORDER MATTERS: `rule.key()` is replaced everywhere FIRST, and only then is the definition inserted. Inserting first
would make the blanket replacement rewrite the very line that defines limitKey.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
NL = chr(10)


def bail(msg):
    subprocess.run(['git', 'checkout', '--', ENG], cwd=WORK)
    print('ROLLED BACK via git checkout (%s)' % msg)
    sys.exit(1)


t = io.open(WORK + '/' + ENG, encoding='utf-8').read()

# 1) every limit site switches to the scoped key
n = t.count('rule.key()')
print('rule.key() sites: %d' % n)
if n != 4:
    bail('expected the four limit sites')
t = t.replace('rule.key()', 'limitKey')

# 2) the one expression, at the top of the loop body
loop = '        for (CompiledRule rule : rules) {'
if t.count(loop) != 1:
    bail('the loop anchor is not unique')
t = t.replace(loop, loop + NL
              + '            // \u2b50 ONE expression, read at all four sites below (2026-09-30; the count-and-reset family).' + NL
              + '            // The comment on startTriggerCooldown names the failure this avoids: recording one key while' + NL
              + '            // checking another makes a rule fire forever, or never again.' + NL
              + '            String limitKey = rule.key() + subjectSuffix(rule, ctx);', 1)

# 3) the helper
anchor = '    private static void applyOne(Battle battle, EffectSpec effect, TriggerContext ctx) {'
helper = ('    /**' + NL
          + '     * The suffix that scopes a firing count to another unit, or {@code ""} for the owner.' + NL
          + '     *' + NL
          + '     * <p>\u300c\u8be5\u6548\u679c\u6bcf\u4e2a\u89d2\u8272\u6700\u591a\u89e6\u53d1 1 \u6b21\u300d counts per TRIGGERER, \u300c\u76ee\u6807\u6bcf\u6709 1 \u4e2a\u8d1f\u9762\u6548\u679c\u300d-style' + NL
          + '     * limits count per TARGET. \u26a0 The identity is System.identityHashCode: names are for messages and may' + NL
          + '     * repeat, and the counters live on the combatant, so an identity within one battle is exactly the scope' + NL
          + '     * the counters have.' + NL
          + '     */' + NL
          + '    private static String subjectSuffix(CompiledRule rule, TriggerContext ctx) {' + NL
          + '        String subject = rule.perSubject();' + NL
          + '        if (subject == null || subject.isBlank()) {' + NL
          + '            return "";' + NL
          + '        }' + NL
          + '        CanHit unit = switch (subject) {' + NL
          + '            case "target" -> ctx.target();' + NL
          + '            case "actor" -> ctx.actor();' + NL
          + '            default -> ctx.owner();' + NL
          + '        };' + NL
          + '        return unit == null ? "" : "@" + System.identityHashCode(unit);' + NL
          + '    }' + NL + NL
          + anchor)
if t.count(anchor) != 1:
    bail('the applyOne anchor is not unique')
t = t.replace(anchor, helper, 1)

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
                                               (m.get('message') or '')[:220]))
    for l in ((s.stdout or '') + (s.stderr or '')).split(NL):
        if 'error:' in l or '错误' in l or '.java:' in l:
            print('  DIAG ' + l.strip()[:190])
    bail('suite red')
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: firing counts can be scoped to another unit via per_subject'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
