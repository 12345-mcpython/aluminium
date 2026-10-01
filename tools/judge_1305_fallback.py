# -*- coding: utf-8 -*-
"""Round 703: the judge for the fallback selector, plus the engine mutation.

Two enemies are required: with only one, the fallback and the preference resolve to the SAME unit, so a judge could
not tell them apart (the trap this stretch keeps hitting). CanHit.perish() defeats a unit without hurting it and
without firing any events, and isDeath() alone does not take it out of Battle.enemies -- which is exactly the window
the clause describes.
"""

import glob
import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
JREL = 'src/test/java/com/laosun/aluminium/test/DrRatioFallbackTest.java'
Q, NL = chr(34), chr(10)

JAVA = ('package com.laosun.aluminium.test;' + NL + NL
        + 'import com.laosun.aluminium.Battle;' + NL
        + 'import com.laosun.aluminium.data.TriggerTables;' + NL
        + 'import com.laosun.aluminium.enums.SkillCategory;' + NL
        + 'import com.laosun.aluminium.enums.TriggerEvent;' + NL
        + 'import com.laosun.aluminium.models.Character;' + NL
        + 'import com.laosun.aluminium.models.TriggerInterpreter;' + NL
        + 'import com.laosun.aluminium.models.TriggerTable;' + NL
        + 'import com.laosun.aluminium.models.enemy.Enemy;' + NL
        + 'import com.laosun.aluminium.models.enemy.EnemyFactory;' + NL
        + 'import com.laosun.aluminium.utils.CharacterFactory;' + NL
        + 'import org.junit.jupiter.api.Assertions;' + NL
        + 'import org.junit.jupiter.api.Test;' + NL + NL
        + 'import java.util.List;' + NL + 'import java.util.Random;' + NL + NL
        + '/**' + NL
        + ' * 1305 Dr. Ratio: 「若追加攻击施放前目标被消灭则对敌方随机单体发动」 -- the fallback selector.' + NL
        + ' *' + NL
        + ' * <p><b>Two enemies.</b> With one, `target_else_random_enemy` and the plain `target` resolve to the same' + NL
        + ' * unit, so the judge could not see the difference. <b>A is defeated first</b> via CanHit.perish(), which' + NL
        + ' * fires no events and leaves the unit in Battle.enemies -- the window the clause is about.' + NL
        + ' */' + NL
        + 'public class DrRatioFallbackTest {' + NL
        + '    private static final int RATIO = 1305;' + NL
        + '    private static final int LEVEL = 80;' + NL + NL
        + '    /** Returns how much HP each enemy lost when the follow-up fires with A (dead) as its target. */' + NL
        + '    private static double[] lossesWithDeadTarget() {' + NL
        + '        Character ratio = CharacterFactory.create(RATIO, LEVEL);' + NL
        + '        Enemy a = EnemyFactory.create(1002011, 90, 1);' + NL
        + '        Enemy b = EnemyFactory.create(1002011, 90, 2);' + NL
        + '        Battle battle = new Battle(List.of(ratio), List.of(a, b), new Random(0));' + NL
        + '        battle.startBattle();' + NL
        + '        a.perish();' + NL
        + '        Assertions.assertTrue(a.isDeath(), ' + Q + 'the fixture must leave A defeated' + Q + ');' + NL
        + '        Assertions.assertTrue(battle.enemies.contains(a),' + NL
        + '                ' + Q + 'isDeath() alone must not remove it -- that is the window the clause is about' + Q + ');' + NL
        + '        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(ratio, ratio, a, 1, 0, null, battle,' + NL
        + '                SkillCategory.UNSPECIFIED);' + NL
        + '        var rules = TriggerTables.of(RATIO).rulesFor(TriggerEvent.ALLY_ATTACK).stream()' + NL
        + '                .filter(r -> ' + Q + 'talent_chance_followup' + Q + '.equals(r.id())).toList();' + NL
        + '        Assertions.assertEquals(1, rules.size(), ' + Q + 'the follow-up rule must exist' + Q + ');' + NL
        + '        double aBefore = a.getCurrentHp();' + NL
        + '        double bBefore = b.getCurrentHp();' + NL
        + '        TriggerInterpreter.apply(battle, rules.getFirst(), ctx);' + NL
        + '        return new double[]{aBefore - a.getCurrentHp(), bBefore - b.getCurrentHp()};' + NL
        + '    }' + NL + NL
        + '    @Test' + NL
        + '    public void aDeadTargetFallsBackToAnotherEnemy() {' + NL
        + '        double[] loss = lossesWithDeadTarget();' + NL
        + '        Assertions.assertEquals(0.0, loss[0], 1e-9,' + NL
        + '                ' + Q + 'the defeated preferred target must not be hit -- CanHit refuses damage on a dead unit' + Q + ');' + NL
        + '        Assertions.assertTrue(loss[1] > 0,' + NL
        + '                ' + Q + 'with the preferred target dead the fallback must reach another enemy, got ' + Q + ' + loss[1]);' + NL
        + '        System.out.println(' + Q + '[1305] fallback ok: deadTargetLoss=' + Q + ' + loss[0] + ' + Q + ' otherLoss=' + Q + ' + loss[1]);' + NL
        + '    }' + NL + '}' + NL)
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
eng_saved = io.open(WORK + '/' + ENG, encoding='utf-8').read()
print('judge written')


def bail(msg):
    if os.path.exists(WORK + '/' + JREL):
        os.remove(WORK + '/' + JREL)
    subprocess.run(['git', 'checkout', '--', ENG], cwd=WORK)
    print('ROLLED BACK (%s)' % msg)
    sys.exit(1)


def focused():
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        os.remove(p)
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.DrRatioFallbackTest',
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
                    if ln.strip().startswith('[1305]'):
                        printed.append(ln.strip())
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    reds += 1
                    msgs.append((m.get('message') or '')[:230])
    return r.returncode, reds, msgs, printed, ((r.stdout or '') + (r.stderr or ''))


code, reds, msgs, printed, out = focused()
print('focused exit %d (reds %d)' % (code, reds))
for l in printed[:1]:
    print('  ' + l[:170])
if code != 0:
    for m in msgs[:3]:
        print('  XMLFAIL ' + m)
    for l in out.strip().split(NL)[-6:]:
        print('  RAW ' + l.strip()[:150])
    bail('the judge is red')

mut_old = 'yield preferred != null && !preferred.isDeath()'
print('mutation anchor: %d' % eng_saved.count(mut_old))
if eng_saved.count(mut_old) != 1:
    bail('the mutation anchor is not unique')
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(
    eng_saved.replace(mut_old, 'yield preferred != null && preferred.isDeath()', 1))
_, r2, _, _, _ = focused()
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(eng_saved)
print('mutation (predicate inverted) is %s (reds=%s)' % ('GREEN (blind!)' if r2 == 0 else 'red', r2))
if r2 == 0:
    bail('the mutation is invisible')
s = subprocess.run([r'.\gradlew.bat', 'test', '--rerun-tasks', '--quiet', '--console=plain'], cwd=WORK,
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
print('suite %d' % s.returncode)
if s.returncode != 0:
    bail('suite red')
g = [subprocess.run([r'.\gradlew.bat', 'run', *a, '--quiet', '--console=plain'], cwd=WORK,
                    capture_output=True, text=True).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'test: the fallback selector needs two enemies, with an engine mutation'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
