# -*- coding: utf-8 -*-
"""Round 673: the judge that sees 1312's per-point energy, plus an engine mutation.

MishaTest spends one point per call, where per-action and per-point agree, so it cannot see round 672's change. This
judge hands the shipped rule a context whose amount is 2 and expects 4 energy. The mutation disables the
`amount_from_event` branch that `scaledAmount` reads -- shared with 1505, so other judges may go red too, which is fine.
Removes the judge on ANY failure, so the tree is clean either way.
"""

import glob
import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
JREL = 'src/test/java/com/laosun/aluminium/test/MishaPerPointTest.java'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'

JAVA = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerInterpreter;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1312 Misha: 「我方全体每消耗 1 个战技点…米沙恢复 2.00 点能量」 -- 2 PER POINT, not per spending action.
 *
 * <p>Shipped 2026-09-30 by adding `amount_from_event: true` + `amount_percent: 2`, a general field that
 * `requireAmount` already exempts. MishaTest spends one point per call, where the two readings agree, so it cannot see
 * this; this judge hands the rule a context whose amount is 2 and expects 4 energy.
 */
public class MishaPerPointTest {
    private static final int MISHA = 1312;
    private static final int LEVEL = 80;

    private static double energyAfter(int pointsSpent) {
        Character c = CharacterFactory.create(MISHA, LEVEL, true, null, null);
        Enemy e = EnemyFactory.create(1002011, 90, 1);
        Battle b = new Battle(List.of(c), List.of(e), new Random(0));
        b.startBattle();
        c.setCurrentEnergy(0);
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(c, c, e, 1, pointsSpent, null, b,
                SkillCategory.UNSPECIFIED);
        var rules = TriggerTables.of(MISHA).rulesFor(TriggerEvent.SKILL_POINT_SPENT).stream()
                .filter(r -> "talent_energy_on_skill_point_spent".equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size(), "the energy rule must exist");
        double before = c.getCurrentEnergy();
        TriggerInterpreter.apply(b, rules.getFirst(), ctx);
        return c.getCurrentEnergy() - before;
    }

    @Test
    public void twoSpentPointsReturnFourEnergy() {
        Assertions.assertEquals(2.0, energyAfter(1), 1e-6, "one point is 2 energy");
        Assertions.assertEquals(4.0, energyAfter(2), 1e-6,
                "two points must be 4 energy -- that is what per-point means, and a per-action reading gives 2");
        System.out.println("[1312] per-point ok: 1 point -> " + energyAfter(1) + ", 2 points -> " + energyAfter(2));
    }
}
'''
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
eng_saved = io.open(WORK + '/' + ENG, encoding='utf-8').read()
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
            for chunk in (el.text, el.tail):
                for line in (chunk or '').split('\n'):
                    if line.strip().startswith('[1312]'):
                        printed.append(line.strip())
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    reds += 1
                    msgs.append((m.get('message') or '')[:230])
    return r.returncode, reds, msgs, printed, ((r.stdout or '') + (r.stderr or ''))


code, reds, msgs, printed, out = focused()
print('focused exit: %d (reds=%d)' % (code, reds))
for l in printed[:2]:
    print('  ' + l[:170])
if code != 0:
    for m in msgs[:3]:
        print('  XMLFAIL ' + m)
    for l in out.strip().split('\n')[-6:]:
        print('  RAW ' + l.strip()[:150])
    os.remove(WORK + '/' + JREL)
    print('ROLLED BACK the judge')
    sys.exit(1)

mut_old = 'if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {'
print('mutation anchor count: %d' % eng_saved.count(mut_old))
if eng_saved.count(mut_old) != 1:
    os.remove(WORK + '/' + JREL)
    print('REFUSING: mutation anchor not unique; judge removed')
    sys.exit(1)
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(
    eng_saved.replace(mut_old, 'if (false) {  // MUTATION', 1))
_, reds2, msgs2, _, _ = focused()
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(eng_saved)
print('engine restored; the amount_from_event mutation is %s (reds=%s)' % ('GREEN (blind!)' if reds2 == 0 else 'red', reds2))
if reds2 == 0:
    for m in msgs2[:2]:
        print('  why: ' + m)
    os.remove(WORK + '/' + JREL)
    print('ROLLED BACK')
    sys.exit(1)

suite = subprocess.run([r'.\gradlew.bat', 'test', '--rerun-tasks', '--quiet', '--console=plain'], cwd=WORK,
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    os.remove(WORK + '/' + JREL)
    print('ROLLED BACK (suite red)')
    sys.exit(1)
gates = [subprocess.run([r'.\gradlew.bat', 'run', *a, '--quiet', '--console=plain'], cwd=WORK,
                        capture_output=True, text=True).returncode for a in ([], ['--args=mechanics'])]
print('gates: %s' % gates)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'test: 1312 energy follows the spent points, with an engine mutation'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
