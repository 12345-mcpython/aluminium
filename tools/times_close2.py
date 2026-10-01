# -*- coding: utf-8 -*-
"""Round 633: write the times judge and close the capability, with an automatic rollback on failure."""

import glob
import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
JREL = 'src/test/java/com/laosun/aluminium/test/TimesRepeatTest.java'
FX = 'src/test/resources/relic_sets/99004.json'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'

JAVA = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerInterpreter;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Effect-level repetition (2026-09-30): the {@code times} field on an effect.
 *
 * <p>The fixture set 99004 holds two rules differing in nothing but {@code times} (3 vs 1), so the observable is how
 * much damage ONE battle produces -- an absolute figure would drag defence and mitigation into the assertion.
 *
 * <p><b>Both sides must be one battle that settles three times.</b> Measured (rounds 631/632): one application of
 * {@code times: 3} against three applications of {@code times: 1} gave 1159.30 against 993.69, because each
 * application in a FRESH battle restarts the RNG at Random(0), while times: 3 advances the battle RNG three times and
 * can crit on different rolls. Three settlements inside one battle consume exactly what one times: 3 consumes.
 *
 * <p>Nine shipped clauses are registered on this shape; 8005 is the one that has shipped.
 */
public class TimesRepeatTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;

    /** Applies the named rule of 99004 applications times INSIDE one battle; returns the enemy HP loss. */
    private static double lossWith(String ruleId, int applications) {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(99004, 5, 15));
        Enemy e = EnemyFactory.create(1002011, 90, 1);
        Battle b = new Battle(List.of(c), List.of(e), new Random(0));
        b.startBattle();
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(c, c, e, 1, 0, null, b,
                SkillCategory.UNSPECIFIED);
        var rules = RelicTriggerTables.of(99004).at(2)
                .matching(TriggerEvent.BATTLE_START, ctx).stream()
                .filter(r -> ruleId.equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size(), ruleId + " must match");
        double before = e.getCurrentHp();
        for (int i = 0; i < applications; i++) {
            TriggerInterpreter.apply(b, rules.getFirst(), ctx);
        }
        return before - e.getCurrentHp();
    }

    @Test
    public void oneTimesThreeEqualsThreeTimesOneInOneBattle() {
        double three = lossWith("times_three", 1);
        double oneThreeTimes = lossWith("times_one", 3);
        Assertions.assertTrue(three > 0, "times 3 must deal something (got " + three + ")");
        Assertions.assertEquals(oneThreeTimes, three, 1e-6,
                "one settlement repeated 3 times must equal three single settlements in one battle -- that is what "
                        + "times means (times_three=" + three + ", 3x times_one=" + oneThreeTimes + ")");
        System.out.println("[times] repeat ok: times_three=" + three + " 3x times_one=" + oneThreeTimes);
    }
}
'''
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
fx_saved = io.open(WORK + '/' + FX, encoding='utf-8').read()
eng_saved = io.open(WORK + '/' + ENG, encoding='utf-8').read()
print('judge written')


def focused():
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        os.remove(p)
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.TimesRepeatTest',
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
            for chunk in (el.text, el.tail):
                for line in (chunk or '').split('\n'):
                    if line.strip().startswith('[times]'):
                        printed.append(line.strip())
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    reds += 1
                    msgs.append((m.get('message') or '')[:230])
    return r.returncode, reds, msgs, printed


code, reds, msgs, printed = focused()
print('focused exit: %d (reds=%d)' % (code, reds))
for l in printed[:2]:
    print('  ' + l[:170])
if code != 0:
    for m in msgs[:3]:
        print('  XMLFAIL ' + m)
    os.remove(WORK + '/' + JREL)
    io.open(WORK + '/' + FX, 'w', encoding='utf-8', newline='').write(fx_saved)
    print('ROLLED BACK the judge and the fixture')
    sys.exit(1)

mut_old = 'int times = effect.getTimes() == null ? 1 : effect.getTimes();'
print('mutation anchor count: %d' % eng_saved.count(mut_old))
if eng_saved.count(mut_old) != 1:
    print('REFUSING: mutation anchor not unique')
    sys.exit(1)
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(
    eng_saved.replace(mut_old, 'int times = 1;  // MUTATION', 1))
_, reds2, msgs2, _ = focused()
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(eng_saved)
print('engine restored; the times mutation is %s (reds=%s)' % ('GREEN (blind!)' if reds2 == 0 else 'red', reds2))
if reds2 == 0:
    for m in msgs2[:2]:
        print('  why: ' + m)
    os.remove(WORK + '/' + JREL)
    io.open(WORK + '/' + FX, 'w', encoding='utf-8', newline='').write(fx_saved)
    print('ROLLED BACK')
    sys.exit(1)

suite = subprocess.run([r'.\gradlew.bat', 'test', '--rerun-tasks', '--quiet', '--console=plain'], cwd=WORK,
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    os.remove(WORK + '/' + JREL)
    io.open(WORK + '/' + FX, 'w', encoding='utf-8', newline='').write(fx_saved)
    print('ROLLED BACK (suite red)')
    sys.exit(1)
gates = [subprocess.run([r'.\gradlew.bat', 'run', *a, '--quiet', '--console=plain'], cwd=WORK,
                        capture_output=True, text=True).returncode for a in ([], ['--args=mechanics'])]
print('gates: %s' % gates)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'test: judge effect-level repetition inside one battle, with an engine mutation'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
