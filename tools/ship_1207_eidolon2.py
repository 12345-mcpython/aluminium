# -*- coding: utf-8 -*-
"""Round 778: 1207 Yukong's Eidolon 2 -- 「我方任意单体当前能量值等于其能量上限时，驭空额外恢复 5 点能量。
该效果我方每个单体仅可触发 1 次，当驭空施放终结技后，重置该效果触发次数。」

⚠ The subject is 「我方任意单体」, so the condition is `actor is_ally` and NOT `actor == self` -- 1310's lookalike
clause really is about the caster herself. Copying that condition would silently drop every teammate.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1207.json'
NL = chr(10)
NEW_ID = 'eidolon2_energy_on_ally_full'

path = WORK + '/' + CHAR
doc = json.load(io.open(path, encoding='utf-8'))
rules = doc if isinstance(doc, list) else doc.get('rules')
if not isinstance(rules, list):
    print('REFUSING: unknown shape')
    sys.exit(1)
if any(isinstance(r, dict) and r.get('id') == NEW_ID for r in rules):
    print('already shipped')
    sys.exit(0)

rules.append({
    'on': 'ENERGY_GAINED',
    'id': NEW_ID,
    'when': ['actor is_ally', 'self_energy_percent >= 1.0'],
    'do': [{'op': 'GAIN_ENERGY', 'amount': 5, 'target': 'self'}],
    'per_turn': 1,
    'per_subject': 'actor',
    'min_eidolon': 2,
    'source': '1207 驭空 星魂 2（E:\\turnbasedgamedata\\aluminium_texts\\1207_驭空.md）: '
              '「当我方任意单体当前能量值等于其能量上限时，驭空额外恢复5点能量。该效果我方每个单体仅可触发1次，'
              '当驭空施放终结技后，重置该效果触发次数。」',
    'note': ('⭐ 三个维度都已具备 ✓（2026-09-30 ✓）：⚠ 「**1 次**」＝ `per_turn: 1` ✗ ＋ ⚠ 「我方**每个单体**」'
             '＝ `per_subject: actor` ✗（⚠ 计数落在**那个能量满的单体**身上 ✓，⚠ 因为 `ENERGY_GAINED` 的 `actor` '
             '就是它 ✓）＋ ⚠ 「**重置**」＝ 另一条 `ULT_CAST` 规则 ✓。⚠⚠ **条件不能照抄 1310** ✗：'
             '⚠ `1310` 的同类句 ⚠ 「当**能量恢复至上限时**」✗（⚠ 主语是**她自己** ✓）⇒ 写 `actor == self` ✓；'
             '⚠ 而本句的主语是 ⚠ 「我方**任意单体**」✗ ⇒ 必须写 ⚠ `actor is_ally` ✓ —— ⚠ 照抄会得到'
             '「只有驭空自己能量满时才回能」✗ ⚠，⚠ 一个**看起来正常、实际漏掉队友**的近似 ✓。'
             '⚠ 事件 `ENERGY_GAINED` ✓ 与条件 `self_energy_percent >= 1.0` ✗ 都是既有词汇 ✓'
             '（⚠ 后者由 `1310` 那条出货时新增 ✓）。'),
})
rules.append({
    'on': 'ULT_CAST',
    'id': 'ult_resets_the_eidolon2_energy_count',
    'when': ['actor == self'],
    'do': [{'op': 'RESET_TRIGGER_LIMIT', 'rule': NEW_ID, 'target': 'self'}],
    'source': '1207 驭空 星魂 2 后半: 「当驭空施放终结技后，重置该效果触发次数。」',
    'note': ('⭐ 「重置」那一半 ✓（2026-09-30 ✓）：⚠ 只清 ⚠ `eidolon2_energy_on_ally_full` 这一条的计数 ✓'
             '（⚠ 用 `RESET_TRIGGER_LIMIT` ✗），⚠ 而不是清她全部规则的计数 ✓（⚠ 她 `ULT_CAST` 上还有别的规则 ✓）。'),
})
text = json.dumps(doc, ensure_ascii=False, indent=2) + NL
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('1207 rules: %d（新增两条 ✓）' % len(rules))


def bail(msg):
    subprocess.run(['git', 'checkout', '--', CHAR], cwd=WORK)
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
g = [run('run', *a).returncode for a in ([], ['--args=mechan ']) ]
g = [run('run', *a).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: 1207 eidolon 2 restores 5 energy per ally once, reset by her ultimate'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
