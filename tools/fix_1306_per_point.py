# -*- coding: utf-8 -*-
"""Round 665: make 1306's phantasm stack follow the event's amount, which the engine already supports.

`addStack` honours `scale: event_amount` with `percent` (built for cone 23021, whose comment says: gainSkillPoint(2)
raises ONE event carrying 2, and 「每 1 个」 means two marks). The rule currently hard-codes `amount: 1`, so a spend of
two points grants one mark -- the approximation its own note declares. Patching at the JSON level, not by text anchor.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1306.json'
RID = 'talent_phantasm_stack'

path = WORK + '/' + CHAR
doc = json.load(io.open(path, encoding='utf-8'))
where = doc if isinstance(doc, list) else doc.get('rules')
if not isinstance(where, list):
    print('REFUSING: unknown shape')
    sys.exit(1)
target = next((r for r in where if isinstance(r, dict) and r.get('id') == RID), None)
if target is None:
    print('REFUSING: %s not found' % RID)
    sys.exit(1)
entry = target['do'][0]
print('before: %s' % json.dumps(entry, ensure_ascii=False))
if entry.get('op') != 'ADD_STACK' or 'amount' not in entry:
    print('REFUSING: unexpected shape for %s' % RID)
    sys.exit(1)
entry.pop('amount', None)
entry['scale'] = 'event_amount'
entry['percent'] = 1
note = ('⭐ **每点一层，而非每次动作一层** ✓（2026-09-30 修订 ✓）：⚠ 原先写 `amount: 1` ✗ ⇒ '
        '⚠ **一次花 2 点只得 1 层** ✗（⚠ 原注记已声明该差异 ✓）。⚠ 引擎早已支持 ⚠ `scale: event_amount` ＋ '
        '`percent` ✗ —— ⚠ `addStack` 里为**光锥 23021**（「每恢复 1 个战技点，获得 1 层【彩焰】」✓）建的那一行 ✓，'
        '⚠ 其注释写明：⚠ `Battle.gainSkillPoint(2)` 发**一个**事件、⚠ 携带 **2** ✓ ⇒ ⚠ 「每 1 个」就是**两层** ✓。'
        '⚠ 所以这是**内容精确化** ✓，⚠ 不是新能力 ✓。')
target['note'] = (str(target.get('note') or '') + ' ' + note).strip()
text = json.dumps(doc, ensure_ascii=False, indent=2) + '\n'
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('after: %s' % json.dumps(entry, ensure_ascii=False))


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
                    print('  XMLFAIL %s#%s %s' % (c.get('classname'), c.get('name'),
                                                  (m.get('message') or '')[:220]))
    subprocess.run(['git', 'checkout', '--', CHAR], cwd=WORK)
    print('ROLLED BACK the content change')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 1306 phantasm stacks follow the spent amount (scale: event_amount)'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
