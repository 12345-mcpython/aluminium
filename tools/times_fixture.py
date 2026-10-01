# -*- coding: utf-8 -*-
"""Round 629: the three groundwork pieces for times' judge -- synthetic set 99004, its rule file, guard sync.

Per the recipe recorded in GAPS (aggro 回收之七百六十). The judge itself comes next; this round only has to leave a
SUITE-GREEN state, which is itself the check that the fixture and the guard agree.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
DATA = 'src/test/resources/data/relic_sets.json'
FX = 'src/test/resources/relic_sets/99004.json'
GUARD = 'src/test/java/com/laosun/aluminium/test/TestRelicSetDataIsInSyncTest.java'

dpath = WORK + '/' + DATA
doc = json.load(io.open(dpath, encoding='utf-8'))
if '99004' in doc:
    print('99004 already present')
else:
    if '99001' not in doc:
        print('REFUSING: 99001 is missing, so there is no shape to clone')
        sys.exit(1)
    clone = json.loads(json.dumps(doc['99001']))
    clone['set_id'] = 99004
    clone['name'] = {'chinese': '测试效果级重复宿主', 'english': 'Synthetic times-fixture host'}
    clone['release_version'] = 'test'
    doc['99004'] = clone
    text = json.dumps(doc, ensure_ascii=False, indent=2) + '\n'
    json.loads(text)
    io.open(dpath, 'w', encoding='utf-8', newline='').write(text)
    print('99004 added to the test-side copy (%d sets)' % len(doc))

RULES = {'1': [
    {'on': 'DEALING_DAMAGE', 'id': 'times_three',
     'when': ['actor == self'],
     'do': [{'op': 'DAMAGE', 'scale': 'self_attr:ATTACK', 'percent': 1.0,
             'element': 'Physical', 'target': 'random_enemy', 'times': 3}],
     'note': '测试夹具：整次结算重复 3 遍（每次重抽随机敌方目标）。'},
    {'on': 'DEALING_DAMAGE', 'id': 'times_one',
     'when': ['actor == self'],
     'do': [{'op': 'DAMAGE', 'scale': 'self_attr:ATTACK', 'percent': 1.0,
             'element': 'Physical', 'target': 'random_enemy', 'times': 1}],
     'note': '测试夹具：同一条但 times: 1，用作比值基线（其余字段逐字相同）。'},
]}
text = json.dumps(RULES, ensure_ascii=False, indent=2) + '\n'
json.loads(text)
io.open(WORK + '/' + FX, 'w', encoding='utf-8', newline='').write(text)
print('99004 rule file written (2 rules)')

gpath = WORK + '/' + GUARD
g = io.open(gpath, encoding='utf-8').read()
old = 'java.util.List.of("99001", "99002")'
new = 'java.util.List.of("99001", "99002", "99004")'
print('guard anchor count: %d' % g.count(old))
if g.count(old) != 1:
    print('REFUSING: the guard list anchor is not unique')
    sys.exit(1)
io.open(gpath, 'w', encoding='utf-8', newline='').write(g.replace(old, new, 1))
print('copy-sync guard list now names three synthetic sets')


def run(*a):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + ['--quiet', '--console=plain'], cwd=WORK,
                          capture_output=True, text=True, encoding='utf-8', errors='replace')


suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    import glob as gg
    import xml.etree.ElementTree as E
    for p in gg.glob(WORK + '/build/test-results/test/*.xml'):
        try:
            r = E.parse(p).getroot()
        except Exception:
            continue
        for c in r.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    print('  XMLFAIL %s#%s %s' % (c.get('classname'), c.get('name'),
                                                  (m.get('message') or '')[:220]))
    print('NOT rolled back automatically')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'test: synthetic set 99004 with a times: 3 / times: 1 pair, for the ratio judge'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
