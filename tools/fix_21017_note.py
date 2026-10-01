# -*- coding: utf-8 -*-
"""Round 578: 21017's note is stale -- the energy condition it names as missing now EXISTS.

Its note said "引擎没有当前能量 == 上限这一条件". That stopped being true on 2026-09-30, when self_energy_percent
was added and closed (rounds 562-567). The real blocker is a different, larger one: a modifier whose existence is
scoped to a condition, i.e. one that is removed when the condition stops holding.
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CONE = 'src/main/resources/light_cones/21017.json'

OLD = '（`self_max_energy` 只是上限 ✓），且它与第一句**作用于同一属性** ✓ ⇒ 不能近似 ✗ ⇒ 按纪律**登记而不猜** ✓。'
NEW = ('⭐ **阻碍已于 2026-09-30 重新核实** ✓：原写「引擎没有当前能量等于上限这一条件」✗，'
       '**该句已不再为真** ✓ —— `self_energy_percent` **已于本日加入并闭环** ✓'
       '（⚠ 含判据 ＋ 引擎级变异 ✓；⚠ 读者是角色 1310 天赋的第一条 ✓）。'
       '⚠ 现在真正的阻碍是**另一个、更大的** ✗：本句是**持续状态**（「当…时」＝while ✗），'
       '⚠ 而引擎缺「**条件作用域的修饰器**」✗ —— ⚠ 即「条件不再成立时**自动移除**」这一层 ✗。'
       '⚠ 若用事件重挂 ✗：同族规则会**替换** ✓（⚠ 数值那一刻是对的 ✓），'
       '⚠ 但能量掉回未满之后**没有任何东西撤销它** ✗ ⇒ ⚠ 会永久停在 #1+#2 ✗（**没有症状的错误** ✗）。'
       '⚠ 已搜过引擎 ✓：`NUMERIC_VARIABLES` 含 `self_energy_percent` ✓；'
       '`MODIFY_ATTR` 的时长只有 `permanent` / `turns` / `until` ✗（⚠ 没有「随条件存在」这一种 ✓）。')

for p in [CONE]:
    path = WORK + '/' + p
    orig = io.open(path, encoding='utf-8').read()
    n = orig.count(OLD)
    print('%s anchor count: %d' % (p, n))
    if n == 0:
        print('REFUSING: anchor not found (the note may already be corrected)')
        sys.exit(1)
    if '"' in NEW or "'" in NEW:
        print('REFUSING: replacement carries a quote')
        sys.exit(1)
    patched = orig.replace(OLD, NEW)
    json.loads(patched)
    io.open(path, 'w', encoding='utf-8', newline='').write(patched)
    print('%s note corrected (all %d occurrences), and it still parses as JSON' % (p, n))


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
    print('NOT rolled back automatically -- fix forward or revert with git checkout')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'docs: 21017 blocker re-verified -- the energy condition exists now; a scoped modifier does not'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
