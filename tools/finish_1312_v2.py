# -*- coding: utf-8 -*-
"""Round 683: finish 1312 -- all five pieces were measured in rounds 680-682.

  * MishaTest line 39 fires SKILL_POINT_SPENT with amount 0 while asserting 2 energy: it was constructing a spend of
    ZERO points, which only passed because the literal amount ignored the context. It becomes 1.
  * 1312 carries `amount_from_event` / `amount_percent`, neither a real key: EffectSpec has no @SerializedName for
    them, so Gson maps the Java names `amountFromEvent` / `amountPercent`, and unknown keys are dropped silently.
  * gainEnergyFor never read the flag (gainResource does, at 1124-1127).
  * Judge measured: 1 -> 2.0, 2 -> 4.0. Mutation (branch off): red.
Rollback goes through `git checkout --` so no line endings change.
"""

import glob
import io
import json
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
CHAR = 'src/main/resources/characters/1312.json'
MISHA = 'src/test/java/com/laosun/aluminium/test/MishaTest.java'
JREL = 'src/test/java/com/laosun/aluminium/test/MishaPerPointTest.java'
Q, NL = chr(34), chr(10)
TOUCHED = [ENG, CHAR, MISHA]


def bail(msg):
    subprocess.run(['git', 'checkout', '--'] + TOUCHED, cwd=WORK)
    if os.path.exists(WORK + '/' + JREL):
        os.remove(WORK + '/' + JREL)
    print('ROLLED BACK via git checkout (%s)' % msg)
    sys.exit(1)


# 1) MishaTest: a spend of ONE point
mt = io.open(WORK + '/' + MISHA, encoding='utf-8').read()
old_mt = 'battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, ally, enemy, 0, 0);'
new_mt = ('// 2026-09-30: the amount is the number of points spent -- 1, not 0. It used to be 0, which only passed\n'
          '        // while the literal `amount: 2` ignored the context entirely (measured rounds 680-682).\n'
          '        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, ally, enemy, 0, 1);')
print('MishaTest anchor: %d' % mt.count(old_mt))
if mt.count(old_mt) != 1:
    bail('MishaTest anchor not unique')
io.open(WORK + '/' + MISHA, 'w', encoding='utf-8', newline='').write(mt.replace(old_mt, new_mt, 1))

# 2) content: the two camelCase keys
doc = json.load(io.open(WORK + '/' + CHAR, encoding='utf-8'))
rules = doc if isinstance(doc, list) else doc.get('rules')
rule = next(r for r in rules if isinstance(r, dict) and r.get('id') == 'talent_energy_on_skill_point_spent')
e = rule['do'][0]
e.pop('amount_from_event', None)
e.pop('amount_percent', None)
e['amountFromEvent'] = True
e['amountPercent'] = 2
rule['note'] = (str(rule.get('note') or '') + ' ⭐ **每点回 2 能量** ✓（2026-09-30 修订 ✓）：'
                '⚠ 原先写 `amount: 2` ✗ ⇒ 一次花 2 点只回 2 点 ✗。⚠ 改用 `amountFromEvent` ＋ `amountPercent` ✗ '
                '—— ⚠ **必须驼峰** ✓（⚠ `EffectSpec` 对这两个字段没有 `@SerializedName` ✓，'
                '⚠ 蛇形键被 Gson 静默丢弃 ✓，本段为此花掉约五轮 ✓）。⚠ 引擎侧：⚠ `gainEnergyFor` 现在读该旗标 ✓ '
                '（⚠ 与 `gainResource` 第 1124–1127 行同形 ✓）。').strip()
io.open(WORK + '/' + CHAR, 'w', encoding='utf-8', newline='').write(json.dumps(doc, ensure_ascii=False, indent=2) + NL)
print('1312 keys: %s' % sorted(e.keys()))

# 3) engine
eng = io.open(WORK + '/' + ENG, encoding='utf-8').read()
OLD = ('    private static void gainEnergyFor(Battle battle, EffectSpec effect, TriggerContext ctx, CanHit target) {'
       + NL + '        if (effect.getScale() == null || effect.getScale().isBlank()) {')
NEW = ('    private static void gainEnergyFor(Battle battle, EffectSpec effect, TriggerContext ctx, CanHit target) {' + NL
       + '        // \u2b50 The event own magnitude (2026-09-30; reader: 1312 per-spent-point energy), as gainResource does.' + NL
       + '        if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {' + NL
       + '            double share = effect.getAmountPercent() == null ? 1 : effect.getAmountPercent();' + NL
       + '            battle.grantEnergy(target, Math.round(ctx.amount() * share));' + NL
       + '            return;' + NL + '        }' + NL
       + '        if (effect.getScale() == null || effect.getScale().isBlank()) {')
print('engine anchor: %d' % eng.count(OLD))
if eng.count(OLD) != 1:
    bail('engine anchor not unique')
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(eng.replace(OLD, NEW, 1))

# 4) the judge
JAVA = ('package com.laosun.aluminium.test;' + NL + NL + 'import com.laosun.aluminium.Battle;' + NL
        + 'import com.laosun.aluminium.data.TriggerTables;' + NL + 'import com.laosun.aluminium.enums.SkillCategory;' + NL
        + 'import com.laosun.aluminium.enums.TriggerEvent;' + NL + 'import com.laosun.aluminium.models.Character;' + NL
        + 'import com.laosun.aluminium.models.TriggerInterpreter;' + NL + 'import com.laosun.aluminium.models.TriggerTable;' + NL
        + 'import com.laosun.aluminium.models.enemy.Enemy;' + NL + 'import com.laosun.aluminium.models.enemy.EnemyFactory;' + NL
        + 'import com.laosun.aluminium.utils.CharacterFactory;' + NL + 'import org.junit.jupiter.api.Assertions;' + NL
        + 'import org.junit.jupiter.api.Test;' + NL + NL + 'import java.util.List;' + NL + 'import java.util.Random;' + NL + NL
        + '/**' + NL + ' * 1312 Misha: 「我方全体每消耗 1 个战技点…米沙恢复 2.00 点能量」 -- 2 PER POINT.' + NL
        + ' *' + NL + ' * <p>MishaTest fires the event with one point; this one hands the rule a context whose amount is 2 and' + NL
        + ' * expects 4, which is the difference a per-action reading cannot show.' + NL + ' */' + NL
        + 'public class MishaPerPointTest {' + NL
        + '    private static double energyAfter(int pts) {' + NL
        + '        Character c = CharacterFactory.create(1312, 80, true, null, null);' + NL
        + '        Enemy e = EnemyFactory.create(1002011, 90, 1);' + NL
        + '        Battle b = new Battle(List.of(c), List.of(e), new Random(0));' + NL
        + '        b.startBattle();' + NL + '        c.setCurrentEnergy(0);' + NL
        + '        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(c, c, e, 1, pts, null, b, SkillCategory.UNSPECIFIED);' + NL
        + '        var rules = TriggerTables.of(1312).rulesFor(TriggerEvent.SKILL_POINT_SPENT).stream()' + NL
        + '                .filter(r -> ' + Q + 'talent_energy_on_skill_point_spent' + Q + '.equals(r.id())).toList();' + NL
        + '        Assertions.assertEquals(1, rules.size());' + NL + '        double before = c.getCurrentEnergy();' + NL
        + '        TriggerInterpreter.apply(b, rules.getFirst(), ctx);' + NL
        + '        return c.getCurrentEnergy() - before;' + NL + '    }' + NL + NL + '    @Test' + NL
        + '    public void twoSpentPointsReturnFourEnergy() {' + NL
        + '        Assertions.assertEquals(2.0, energyAfter(1), 1e-6);' + NL
        + '        Assertions.assertEquals(4.0, energyAfter(2), 1e-6, ' + Q + 'two points must be 4 energy' + Q + ');' + NL
        + '        System.out.println(' + Q + '[1312] per-point ok: 1 -> ' + Q + ' + energyAfter(1) + ' + Q + ', 2 -> ' + Q + ' + energyAfter(2));' + NL
        + '    }' + NL + '}' + NL)
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
print('judge written')


def focused():
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        os.remove(p)
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.MishaPerPointTest',
                        '--console=plain'], cwd=WORK, capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    import xml.etree.ElementTree as E
    reds, msgs, printed = 0, [], []
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        try:
            root = E.parse(p).getroot()
        except Exception:
            continue
        for el in root.iter():
            for ch in (el.text, el.tail):
                for ln in (ch or '').split(NL):
                    if ln.strip().startswith('[1312]'):
                        printed.append(ln.strip())
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    reds += 1
                    msgs.append((m.get('message') or '')[:200])
    return r.returncode, reds, msgs, printed


code, reds, msgs, printed = focused()
print('focused exit %d (reds %d)' % (code, reds))
for l in printed[:1]:
    print('  ' + l[:160])
if code != 0:
    for m in msgs[:2]:
        print('  XMLFAIL ' + m)
    bail('judge red')

patched = io.open(WORK + '/' + ENG, encoding='utf-8').read()
mut = 'if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {' + NL + '            double share = effect.getAmountPercent()'
print('mutation anchor %d' % patched.count(mut))
if patched.count(mut) != 1:
    bail('mutation anchor not unique')
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(
    patched.replace(mut, 'if (false) {' + NL + '            double share = effect.getAmountPercent()', 1))
_, r2, _, _ = focused()
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(patched)
print('mutation is %s' % ('GREEN (blind!)' if r2 == 0 else 'red'))
if r2 == 0:
    bail('mutation invisible')

s = subprocess.run([r'.\gradlew.bat', 'test', '--rerun-tasks', '--quiet', '--console=plain'], cwd=WORK,
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
print('suite %d' % s.returncode)
if s.returncode != 0:
    import xml.etree.ElementTree as E
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        try:
            root = E.parse(p).getroot()
        except Exception:
            continue
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    print('  FAIL %s#%s %s' % (c.get('classname'), c.get('name'), (m.get('message') or '')[:170]))
    bail('suite red')
g = [subprocess.run([r'.\gradlew.bat', 'run', *a, '--quiet', '--console=plain'], cwd=WORK,
                    capture_output=True, text=True).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: GAIN_ENERGY reads amountFromEvent, so 1312 pays per spent point'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
