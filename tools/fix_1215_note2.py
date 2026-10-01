# -*- coding: utf-8 -*-
"""Round 585: 1215's eidolon-2 note, corrected at the JSON level.

Round 584's byte-level anchor did not match (the file's bytes differ from what grep rendered). This walks the parsed
document instead, finds the string that carries the ASCII fragment, and appends the correction to that string.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1215.json'
FRAG = 'per-unit trigger count'

ADD = (' ⚠ RE-VERIFIED 2026-09-30: the FIRST half of that is no longer true -- the ENERGY_GAINED event together '
       'with the self_energy_percent variable do say that energy became full, and the talent of character 1310 '
       'ships exactly that rule. What genuinely remains is the SECOND half: a counter keyed by WHO triggered it, '
       'which no field provides (per_turn and cooldown both count the rule OWNER turns).')
if '"' in ADD or "'" in ADD:
    print('REFUSING: the addition carries a quote')
    sys.exit(1)

path = WORK + '/' + CHAR
doc = json.load(io.open(path, encoding='utf-8'))
hits = []


def walk(node, where):
    if isinstance(node, dict):
        for k, v in node.items():
            if isinstance(v, str) and FRAG in v:
                hits.append((node, k))
            else:
                walk(v, where + '/' + str(k))
    elif isinstance(node, list):
        for i, v in enumerate(node):
            if isinstance(v, str) and FRAG in v:
                hits.append((node, i))
            else:
                walk(v, where + '/' + str(i))


walk(doc, '')
print('strings carrying the fragment: %d' % len(hits))
if len(hits) != 1:
    print('REFUSING: expected exactly one')
    sys.exit(1)
container, key = hits[0]
container[key] = container[key] + ADD
text = json.dumps(doc, ensure_ascii=False, indent=2) + '\n'
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('1215 note corrected, and the file still parses as JSON')


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
    print('NOT auto-rolled-back: the JSON dump may have reformatted the file; revert with git checkout if needed')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'docs: 1215 eidolon 2 -- energy-became-full is expressible; the per-unit counter is not'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
