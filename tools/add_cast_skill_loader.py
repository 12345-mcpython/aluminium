# -*- coding: utf-8 -*-
"""Round 1011: CAST_SKILL must validate its slot at load time, not at run time.

Round 1005 added the op with WIRED + exec branch but no loader check, so a rule missing `skill` would only blow up
when it fired. This adds the check beside COMMAND_SUMMON's.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
NL = chr(10)
t = io.open(WORK + '/' + ENG, encoding='utf-8').read()

anchor = '            case "COMMAND_SUMMON" -> {'
new = (
    '            case "CAST_SKILL" -> {' + NL
    + '                if (effect.getSkill() == null || effect.getSkill().isBlank()) {' + NL
    + '                    throw new IllegalArgumentException("Op CAST_SKILL requires skill, a SkillType slot name (source: "' + NL
    + '                            + spec.getSource() + ")");' + NL
    + '                }' + NL
    + '                try {' + NL
    + '                    SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));' + NL
    + '                } catch (IllegalArgumentException notASlot) {' + NL
    + '                    throw new IllegalArgumentException("Op CAST_SKILL names skill \\"" + effect.getSkill()' + NL
    + '                            + "\\", which is not a SkillType (source: " + spec.getSource() + ")");' + NL
    + '                }' + NL
    + '            }' + NL
    + anchor)
print('anchor: %d' % t.count(anchor))
if t.count(anchor) != 1:
    print('REFUSING: the COMMAND_SUMMON loader anchor is not unique')
    sys.exit(1)
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(t.replace(anchor, new, 1))
print('loader check added')


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
subprocess.run(['git', 'add', ENG, 'tools/add_cast_rule.py'], cwd=WORK, check=True)
subprocess.run(['git', 'commit', '-q', '-m',
                'feat: CAST_SKILL validates its slot at load time, and the tool that writes such rules'],
               cwd=WORK, check=True)
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
