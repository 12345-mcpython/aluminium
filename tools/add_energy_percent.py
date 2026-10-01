# -*- coding: utf-8 -*-
"""Round 562: the `self_energy_percent` numeric variable.

Energy is a FIELD on CanHit, not an AttributeType (the same reason `self_max_energy` needed its own variable at
TriggerTable:3090-3092), so the RATIO current/max also needs one. Its readers are already registered in shipped
notes, which name this missing variable as the blocker:

  * light cone 21017's second sentence  「当前能量值等于其能量上限时，该效果额外提高 #2%」
  * character 1310 clause 3            「战斗开始时若能量不足 50% 则使其恢复至 50%」
  * character 1310 clause 4            「当能量恢复至上限时解除自身所有负面效果」
  * character 1215 eidolon 2           「当我方任意单体当前能量值等于其能量上限时…」
  * light cone 21021's filter          「1 个当前能量百分比小于 50% 的我方其他目标」

Two edits: the name in NUMERIC_VARIABLES, and a case beside `self_max_energy`.
"""

import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
TT = 'src/main/java/com/laosun/aluminium/models/TriggerTable.java'
path = WORK + '/' + TT
saved = io.open(path, encoding='utf-8').read()
cur = saved

a1 = '"target_summon_count", "self_max_energy", "from_skill_id", "target_dot_count",'
b1 = '"target_summon_count", "self_max_energy", "self_energy_percent", "from_skill_id", "target_dot_count",'
if cur.count(a1) != 1:
    print('REFUSING: vocabulary anchor matched %d times' % cur.count(a1))
    sys.exit(1)
cur = cur.replace(a1, b1, 1)
print('1. NUMERIC_VARIABLES gained self_energy_percent')

a2 = '''                case "self_max_energy" -> ctx.owner() == null ? Double.NaN : ctx.owner().getMaxEnergy();
                default -> Double.NaN;'''
b2 = '''                case "self_max_energy" -> ctx.owner() == null ? Double.NaN : ctx.owner().getMaxEnergy();
                // 「当前能量值等于其能量上限」 / 「当前能量百分比小于 50%」 -- the same reasoning as the variable above,
                // one step further: energy is a field rather than an attribute, so the RATIO needs its own name too.
                // Five shipped readers are already on record as blocked on exactly this spelling: light cone 21017's
                // second sentence, character 1310's third and fourth clauses, character 1215's eidolon 2, and the
                // filter light cone 21021 needs. A unit with no energy bar, or a zero maximum, yields NaN -- and
                // every comparison against NaN is false, which is the convention `self_max_energy` already set.
                case "self_energy_percent" -> ctx.owner() == null || ctx.owner().getMaxEnergy() <= 0
                        ? Double.NaN
                        : (double) ctx.owner().getCurrentEnergy() / ctx.owner().getMaxEnergy();
                default -> Double.NaN;'''
if cur.count(a2) != 1:
    print('REFUSING: case anchor matched %d times' % cur.count(a2))
    sys.exit(1)
cur = cur.replace(a2, b2, 1)
print('2. resolveNumeric gained the self_energy_percent case')

io.open(path, 'w', encoding='utf-8', newline='').write(cur)


def run(*a, q=True):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + (['--quiet'] if q else []) + ['--console=plain'],
                          cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')


suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    for l in ((suite.stdout or '') + (suite.stderr or '')).strip().split('\n')[-14:]:
        print('  RAW ' + l.strip()[:175])
    io.open(path, 'w', encoding='utf-8', newline='').write(saved)
    print('rolled back')
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: the self_energy_percent condition variable that five shipped clauses are blocked on'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
