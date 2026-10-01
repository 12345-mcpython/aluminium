# -*- coding: utf-8 -*-
"""Round 601: 1109's M-32 note carries two stale claims.

(1) It says the only thing missing for the talent clause is an op name it could not locate. The clause is written at
    :156 (DEALING_DAMAGE + target has_state 灼烧 => DAMAGE + GAIN_ENERGY), and the op it was looking for is
    ADD_DAMAGE, which has shipped in light cones 20018 and 21030, five ranks each.
(2) It says the traces and eidolons were not read yet -- while the same file has trace rules (trace_play_with_fire).

Method: the exact sentences come from reading the file (round 600), not from grep output. Both replacements are
quote-free, and the patched document is re-parsed before it is trusted.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1109.json'

OLD1 = '唯一缺的是「**追加 1 次附加伤害**」的那个 op 名（引擎里附加伤害是 `DamageType.ADDITIONAL` ✓，但 op 表里对应的拼写我这一轮没能在源码中定位到，**不确定就不写** ✓）；'
NEW1 = ('⭐ **该句已于同档写出** ✓（见本档 `:156` 的规则：`DEALING_DAMAGE` + `target has_state 灼烧` ⇒ `DAMAGE` '
        '+ `GAIN_ENERGY` ✓）；⚠ **本条注记此前说「唯一缺的是那个 op 名」** ✗ —— ⚠ **而它一直存在** ✓：'
        '就是 **`ADD_DAMAGE`** ✓（⚠ 已出货于光锥 `20018` 与 `21030` ✓，各五档 ✓）。**2026-09-30 修订** ✓。'
        '⚠ 以下为**修订前**的记录，保留以便对照：')

OLD2 = '④ 行迹与星魂：本轮**未读**（我的切片只取到 `## 技能` 段 ✓，下一轮补读后再写 ✓）。'
NEW2 = ('④ 行迹与星魂：✅ **已读并写出** ✓（见本档 `trace_play_with_fire` 等规则 ✓）—— '
        '⚠ 原写「本轮未读」✗，**2026-09-30 修订** ✓。')

path = WORK + '/' + CHAR
text = io.open(path, encoding='utf-8').read()
for tag, old in (('op-name', OLD1), ('traces-unread', OLD2)):
    print('%s anchor count: %d' % (tag, text.count(old)))
    if text.count(old) != 1:
        print('REFUSING: %s is not unique' % tag)
        sys.exit(1)
for new in (NEW1, NEW2):
    if '"' in new or "'" in new:
        print('REFUSING: a replacement carries a quote')
        sys.exit(1)
text = text.replace(OLD1, NEW1, 1).replace(OLD2, NEW2, 1)
json.loads(text)
io.open(path, 'w', encoding='utf-8', newline='').write(text)
print('1109 note revised in two places, and the file still parses as JSON')


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
                      'docs: 1109 -- the op name it could not find is ADD_DAMAGE, and the traces were read'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
