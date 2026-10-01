# -*- coding: utf-8 -*-
"""Round 668: 1312's energy follows the spent points, the same way 1306's stacks now do.

Its shipped rule pays a flat 2 energy per spending ACTION; the document says 「每消耗 1 个战技点…恢复 2.00 点能量」,
i.e. 2 per POINT, so two points should pay 4. TriggerInterpreter:1127 shows the amount path honours
`amount_percent` as `round(ctx.amount() * amount_percent)`, so the per-point coefficient is 2.
Rolls back on any failure, including a refusal, so the tree is clean either way.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1312.json'
RID = 'talent_energy_on_skill_point_spent'

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
if entry.get('op') != 'GAIN_ENERGY' or entry.get('amount') != 2:
    print('REFUSING: unexpected shape (expected GAIN_ENERGY amount 2)')
    sys.exit(1)
entry.pop('amount', None)
entry['amount_percent'] = 2
# NOTE: with amount_percent the interpreter computes round(ctx.amount() * 2) and ignores a literal amount.
target['note'] = (str(target.get('note') or '') + ' ⭐ **每点回 2 能量，而非每次动作回 2** ✓'
                  '（2026-09-30 修订 ✓）：⚠ 原先写 `amount: 2` ✗ ⇒ ⚠ **一次花 2 点只回 2 点** ✗'
                  '（⚠ 原注记已声明该差异 ✓）。⚠ 改用 `amount_percent: 2` ✗ —— ⚠ 按 '
                  '`TriggerInterpreter` 第 1127 行 ⚠ `amount = round(ctx.amount() × amount_percent)` ✓。').strip()
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
                                                  (m.get('message') or '')[:230]))
    subprocess.run(['git', 'checkout', '--', CHAR], cwd=WORK)
    print('ROLLED BACK the content change')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 1312 energy follows the spent points, not the spending action'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
