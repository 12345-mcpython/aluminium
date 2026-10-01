# -*- coding: utf-8 -*-
"""Round 593: revise 23057's contradictory note in ALL five ranks.

Round 592's script refused because it assumed the note occurs once; a light cone stores one copy per superimposition
rank, so it occurs five times. This one replaces in every copy and refuses only when there are none.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CONE = 'src/main/resources/light_cones/23057.json'
FRAG = '另一句已登记'
OLD = '⚠ **另一句已登记** ✗：'
NEW = ('✅ **下半句其实已经写好** ✓（见本档 `cone23057_laughter_on_self_ult` ✓ 与 '
       '`cone23057_normal_attack_count` ✓，及其后的重置规则 ✓）—— 本条注记此前与之矛盾 ✗，'
       '**2026-09-30 修订** ✓。⚠ 仍缺的只是「**单体**」这一**形状**层 ✗（⚠ 含自身的**群体**终结技也会算 ✗ —— '
       '详见下方两条注记 ✓）。⚠ 以下为**修订前**的记录，保留以便对照：')
if '"' in NEW or "'" in NEW:
    print('REFUSING: the replacement carries a quote')
    sys.exit(1)

path = WORK + '/' + CONE
doc = json.load(io.open(path, encoding='utf-8'))
hits = []


def walk(node):
    if isinstance(node, dict):
        for k, v in node.items():
            if isinstance(v, str) and FRAG in v:
                hits.append((node, k))
            else:
                walk(v)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            if isinstance(v, str) and FRAG in v:
                hits.append((node, i))
            else:
                walk(v)


walk(doc)
print('notes carrying the stale phrase: %d' % len(hits))
if not hits:
    print('REFUSING: nothing carries the phrase')
    sys.exit(1)
done = 0
for container, key in hits:
    if OLD in container[key]:
        container[key] = container[key].replace(OLD, NEW, 1)
        done += 1
print('revised %d of %d copies' % (done, len(hits)))
text = json.dumps(doc, ensure_ascii=False, indent=2) + '\n'
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('23057 note revised, and the file still parses as JSON')


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
                      'docs: 23057 -- all five ranks: the clause that note called unwritten is implemented'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
