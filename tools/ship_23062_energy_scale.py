# -*- coding: utf-8 -*-
"""Round 797: 23062 随心 -- 「每消耗1点能量值，使本次造成的终结技伤害提高 0.2/0.25/0.3/0.35/0.4%，最多 72/90/108/126/144%」.

The emitter half shipped in b20611d (ULT_CAST now carries the real spend), `scale: event_amount` is the existing
source, and the ceiling's absolute spelling is `cap_amount` (TriggerInterpreter:406: "a constant cap_amount").
The per-point values are fractions: 0.2% = 0.002.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CONE = 'src/main/resources/light_cones/23062.json'
NL = chr(10)
PER_POINT = {'1': 0.002, '2': 0.0025, '3': 0.003, '4': 0.0035, '5': 0.004}
CAP = {'1': 0.72, '2': 0.9, '3': 1.08, '4': 1.26, '5': 1.44}

path = WORK + '/' + CONE
doc = json.load(io.open(path, encoding='utf-8'))
if not isinstance(doc, dict) or set(doc.keys()) != {'1', '2', '3', '4', '5'}:
    print('REFUSING: unexpected shape %s' % (list(doc.keys()) if isinstance(doc, dict) else type(doc)))
    sys.exit(1)
touched = 0
for rank, rules in doc.items():
    target = next((r for r in rules if isinstance(r, dict) and r.get('id') == 'cone23062_king_on_ult'), None)
    if target is None:
        print('REFUSING: rank %s has no cone23062_king_on_ult' % rank)
        sys.exit(1)
    if any(isinstance(e, dict) and e.get('scale') == 'event_amount' for e in (target.get('do') or [])):
        print('rank %s already shipped' % rank)
        continue
    target.setdefault('do', []).append({
        'op': 'BOOST_DAMAGE',
        'scale': 'event_amount',
        'percent': PER_POINT[rank],
        'cap_amount': CAP[rank],
        'target': 'self',
    })
    target['note'] = ((target.get('note') or '')
                      + ' ｜ ⭐ **第 4 句已出货** ✓（2026-09-30 ✓）：「**每消耗 1 点能量值**使本次终结技伤害提高'
                      + ' #3%，**最多** #6%」—— ⚠ `scale: event_amount` ✗（⚠ 本段第 791 轮让 `ULT_CAST` 携带'
                      + '**真实消耗** ✓，⚠ 此前那个位子一直是 `0` ✓）＋ ⚠ 上限用 ⚠ `cap_amount` ✗'
                      + '（⚠ **绝对量** ✓ —— ⚠ 不是 `cap_scale`／`cap_percent` 那套"某属性的份额"✗）。'
                      + '⚠ 每点值取**分数**：⚠ 0.2% ⇒ 0.002 ✓。⚠ 算式是 `percent × scale + amount` ✗'
                      + '（⚠ `TriggerInterpreter:396` ✓）⇒ ⚠ 消耗 50 点 ⇒ ⚠ 0.1 ＝ 10% ✓。')
    touched += 1
text = json.dumps(doc, ensure_ascii=False, indent=2) + NL
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('ranks touched: %d' % touched)


def bail(msg):
    subprocess.run(['git', 'checkout', '--', CONE], cwd=WORK)
    print('ROLLED BACK via git checkout (%s)' % msg)
    sys.exit(1)


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
                                               (m.get('message') or '')[:260]))
    for l in ((s.stdout or '') + (s.stderr or '')).split(NL):
        if 'error:' in l or '错误' in l or '.java:' in l:
            print('  DIAG ' + l.strip()[:190])
    bail('suite red')
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 23062 scales the ultimate damage by the energy it spent, capped absolutely'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
