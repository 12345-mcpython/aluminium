# -*- coding: utf-8 -*-
"""Round 637: mirror 8005's times clause into 8006, and move the pin the pre-flight grep found.

Round 635's grep named three candidate judges; round 636 cleared TrailblazerHarmonyTest (its pins are on BREAK,
ULT_CAST and SKILL_CAST, none on DEALING_DAMAGE), leaving SuperBreakContentTest -- whose own message says the
conversion rule is the file's ONLY DEALING_DAMAGE rule. It becomes 2, and the wording changes with it: a message that
says "only" while the count is 2 would itself be a false ledger.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/8006.json'
TEST = 'src/test/java/com/laosun/aluminium/test/SuperBreakContentTest.java'

RULE = {
    'on': 'DEALING_DAMAGE',
    'id': 'skill_extra_hits_random',
    'when': ['actor == self', 'from_skill SKILL', 'damage_is_attack'],
    'do': [{'op': 'DAMAGE', 'scale': 'self_attr:ATTACK', 'percent': 0.5,
            'element': 'Imaginary', 'target': 'random_enemy', 'times': 4}],
    'source': '8006 开拓者·虚数 战技: 「并额外造成 4 次伤害，每次对随机敌方单体造成 50% 攻击力虚数伤害。」',
    'note': ('⭐ **`times` 的第二个读者，也是 `8005` 的兄弟镜像** ✓ —— ⚠ 「额外造成4次伤害」在 **8005 与 8006 '
             '两份文档里都有** ✓（2026-09-30 实测 ✓）⇒ ⚠ 按 `SiblingHarmonyTest` 的宗旨'
             '（*mirrored where the two documents agree* ✓）本条与 8005 同名同形 ✓。'
             '⚠ 护栏与 8005 一致 ✓：`from_skill SKILL` ✓ ＋ **`damage_is_attack`** ✗（⚠ 不可省 —— 追加实例本身'
             '也是一次 `DEALING_DAMAGE` ✓，⚠ 无护栏会撞上递归上限 ✓，第 630 轮实测过 ✓）＋ `target: random_enemy` '
             '＋ **`times: 4`** ✓。⚠ 本条的落地同时改了一处**被钉死的计数** ✓：'
             '`SuperBreakContentTest` 里 `8006` 的 `DEALING_DAMAGE` 条数 `1 → 2` ✓（⚠ 该处消息原文写的是'
             '*the file ONLY DEALING_DAMAGE rule* ✗，⚠ 一并改写以免消息本身变成假账 ✓）。'),
}

path = WORK + '/' + CHAR
doc = json.load(io.open(path, encoding='utf-8'))
where = doc if isinstance(doc, list) else doc.get('rules')
if not isinstance(where, list):
    print('REFUSING: unknown shape')
    sys.exit(1)
if not any(isinstance(r, dict) and r.get('id') == RULE['id'] for r in where):
    where.append(RULE)
    text = json.dumps(doc, ensure_ascii=False, indent=2) + '\n'
    json.loads(text)
    io.open(path, 'w', encoding='utf-8', newline='').write(text)
    print('8006 mirrored (%d rules now)' % len(where))
else:
    print('8006 already mirrors it')

tpath = WORK + '/' + TEST
t = io.open(tpath, encoding='utf-8').read()
pairs = [
    ('assertEquals(1, TriggerTables.of(HARMONY).ruleCount(TriggerEvent.DEALING_DAMAGE)',
     'assertEquals(2, TriggerTables.of(HARMONY).ruleCount(TriggerEvent.DEALING_DAMAGE)'),
    ('the file\'s only DEALING_DAMAGE rule',
     'one of the file\'s DEALING_DAMAGE rules (2 since the skill extra-hits clause landed)'),
]
for old, new in pairs:
    n = t.count(old)
    print('test anchor %r count: %d' % (old[:34], n))
    if n != 1:
        print('REFUSING: that test anchor is not unique')
        subprocess.run(['git', 'checkout', '--', CHAR], cwd=WORK)
        sys.exit(1)
    t = t.replace(old, new, 1)
io.open(tpath, 'w', encoding='utf-8', newline='').write(t)
print('SuperBreakContentTest pin: 1 -> 2, wording updated')


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
    subprocess.run(['git', 'checkout', '--', CHAR, TEST], cwd=WORK)
    print('ROLLED BACK the mirror and the pin change')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: mirror the times clause into 8006 and move the pinned count it touches'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
