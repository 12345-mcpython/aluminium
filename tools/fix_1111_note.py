# -*- coding: utf-8 -*-
"""Round 598: 1111's note claimed two clauses were still registered; the same file implements both.

characters/1111.json:82 says the two enhanced-basic-attack clauses still need from_skill_id (verified feasible but
not landed). The file has `from_skill_id == 8` at lines 89 and 108 with full notes at 101 and 119. The old sentence is
kept as a superseded record, so the correction is auditable.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1111.json'
FRAG = '仍登记'
OLD = '⚠ **仍登记**：强化普攻「消耗 2 层【斗志】」与「使裂伤立即结算 85%」需要 `from_skill_id`（已验证可行、未落地）。'
NEW = ('✅ **这两句其实已经写好** ✓（见本档 `from_skill_id == 8` 的两条规则 ✓，'
       '以及它们各自的注记 ✓）—— 本条注记此前与之矛盾 ✗，**2026-09-30 修订** ✓。'
       '⚠ 以下为**修订前**的记录，保留以便对照：')

path = WORK + '/' + CHAR
doc = json.load(io.open(path, encoding='utf-8'))
hits = []


def walk(node):
    if isinstance(node, dict):
        for k, v in node.items():
            if isinstance(v, str) and OLD in v:
                hits.append((node, k))
            else:
                walk(v)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            if isinstance(v, str) and OLD in v:
                hits.append((node, i))
            else:
                walk(v)


walk(doc)
print('strings carrying the exact stale sentence: %d' % len(hits))
if not hits:
    print('REFUSING: nothing carries it (already corrected?)')
    sys.exit(1)
if '"' in NEW or "'" in NEW:
    print('REFUSING: the replacement carries a quote')
    sys.exit(1)
for container, key in hits:
    container[key] = container[key].replace(OLD, NEW, 1)
text = json.dumps(doc, ensure_ascii=False, indent=2) + '\n'
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('1111 note revised, and the file still parses as JSON')


def run(*a, q=True):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + (['--quiet'] if q else []) + ['--console=plain'],
                          cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')


suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
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
                    print('  XMLFAIL ' + (m.get('message') or '')[:300])
    print('NOT auto-rolled-back: revert with git checkout if needed')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'docs: 1111 -- the two clauses its note called unlanded are implemented below it'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
