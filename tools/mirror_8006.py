# -*- coding: utf-8 -*-
"""Round 634: mirror 8005's times clause into 8006 -- the sibling discipline requires it, and both documents state it.

Both aluminium_texts documents contain 「额外造成4次伤害」 (measured 2026-09-30), and SiblingHarmonyTest's own header
says the pair is "mirrored where the two documents agree". 8006 already carries one DEALING_DAMAGE rule (SUPER_BREAK),
so its count there becomes 2.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/8006.json'

RULE = {
    'on': 'DEALING_DAMAGE',
    'id': 'skill_extra_hits_random',
    'when': ['actor == self', 'from_skill SKILL', 'damage_is_attack'],
    'do': [{'op': 'DAMAGE', 'scale': 'self_attr:ATTACK', 'percent': 0.5,
            'element': 'Imaginary', 'target': 'random_enemy', 'times': 4}],
    'source': '8006 开拓者·虚数 战技: 「并额外造成 4 次伤害，每次对随机敌方单体造成 50% 攻击力虚数伤害。」',
    'note': ('⭐ **`times` 的第二个读者，也是 `8005` 的兄弟镜像** ✓ —— ⚠ 「额外造成4次伤害」在 **8005 与 8006 '
             '两份文档里都有** ✓（本日实测 ✓：`aluminium_texts/8005_开拓者.md` ✓ 与 `8006_开拓者.md` ✓）⇒ '
             '⚠ 按 `SiblingHarmonyTest` 的宗旨（*mirrored where the two documents agree* ✓）本条与 8005 同名同形 ✓。'
             '⚠ 写法与护栏与 8005 完全一致 ✓：`from_skill SKILL` ✓ ＋ **`damage_is_attack`** ✗（⚠ 不可省 —— '
             '追加实例本身也是一次 `DEALING_DAMAGE` ✓，⚠ 无护栏会撞上引擎的递归上限 ✓，第 630 轮实测过 ✓）'
             '＋ `target: random_enemy` ＋ **`times: 4`** ✓。'),
}

path = WORK + '/' + CHAR
doc = json.load(io.open(path, encoding='utf-8'))
where = doc if isinstance(doc, list) else doc.get('rules')
if not isinstance(where, list):
    print('REFUSING: unknown shape')
    sys.exit(1)
if any(isinstance(r, dict) and r.get('id') == RULE['id'] for r in where):
    print('already mirrored')
    sys.exit(0)
where.append(RULE)
text = json.dumps(doc, ensure_ascii=False, indent=2) + '\n'
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('8006 mirrored (%d rules now)' % len(where))


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
                                                  (m.get('message') or '')[:200]))
    subprocess.run(['git', 'checkout', '--', CHAR], cwd=WORK)
    print('ROLLED BACK the mirror')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'content: mirror 8005 times clause into 8006, as the sibling discipline requires'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
