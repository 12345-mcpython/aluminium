# -*- coding: utf-8 -*-
"""Round 849: prove the judge with a mutation -- small, and restore in a `finally`.

Round 845's crash between "mutate" and "restore" left the mutation in the pushed tree; a `finally` is what makes that
impossible, and it is why this script does nothing else.
"""

import glob
import io
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
BATTLE = 'src/main/java/com/laosun/aluminium/Battle.java'
TEST = 'com.laosun.aluminium.test.IAmAsYouBeholdSpendTest'
NL = chr(10)

path = WORK + '/' + BATTLE
saved = io.open(path, encoding='utf-8').read()
mut_re = re.compile(r'\n[ \t]*damage\.withCastEnergySpent\(lastUltEnergySpent\);')
print('mutation matches: %d' % len(mut_re.findall(saved)))
if os.environ.get('DSH_GOAL_ROUND') == 'skip':
    sys.exit(1)
if len(mut_re.findall(saved)) != 1:
    print('REFUSING: the mutation anchor is not unique')
    sys.exit(1)


def run():
    for f in glob.glob(WORK + r'\build\test-results\test\*IAmAsYouBeholdSpendTest*.xml'):
        os.remove(f)
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', TEST, '--rerun-tasks', '--console=plain'],
                       cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')
    reds, outs = [], []
    for f in glob.glob(WORK + r'\build\test-results\test\*IAmAsYouBeholdSpendTest*.xml'):
        try:
            root = ET.parse(f).getroot()
        except Exception:
            continue
        for el in root.iter():
            for ch in (el.text, el.tail):
                for ln in (ch or '').split(NL):
                    if ln.strip().startswith('[23062]'):
                        outs.append(ln.strip())
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                if c.find(k) is not None:
                    reds.append(c.get('name'))
    return r.returncode, reds, outs


try:
    io.open(path, 'w', encoding='utf-8', newline='').write(
        mut_re.sub(NL + '            // MUTATION: the spend never reaches the instance', saved))
    code, reds, outs = run()
    print('MUTATED : exit %d ; reds=%s' % (code, reds))
    for o in outs[:2]:
        print('  OUT %s' % o[:190])
finally:
    io.open(path, 'w', encoding='utf-8', newline='').write(saved)
    print('restored in finally: %s' % ('same' if io.open(path, encoding='utf-8').read() == saved else 'DIFF'))

code2, reds2, outs2 = run()
print('RESTORED: exit %d ; reds=%s' % (code2, reds2))
for o in outs2[:2]:
    print('  OUT %s' % o[:190])
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
