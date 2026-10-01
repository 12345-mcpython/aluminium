# -*- coding: utf-8 -*-
"""Round 666: the judge that can see the difference, plus the engine mutation.

The shipped judges all spend ONE skill point per call, so per-action and per-point agree there and they cannot see the
change made in round 665. This judge applies the shipped rule with a hand-built context whose amount is 2 and asserts
TWO stacks -- which is exactly what `scale: event_amount` buys. The mutation removes that branch, so the two-amount
case collapses to one stack and the judge must go red.
"""

import io
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
JREL = 'src/test/java/com/laosun/aluminium/test/SparklePerPointTest.java'
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
 * 1306 Sparkle: 「当我方目标每消耗 1 点战技点，花火获得 1 层【幻相】」 -- PER POINT, not per spending action.
 *
 * <p>Shipped 2026-09-30 by giving the rule `scale: event_amount` + `percent: 1`, which `addStack` already honoured
 * (built for cone 23021). The other 1306 judges all spend one point per call, where per-action and per-point agree, so
 * they cannot see this; this one hands the rule a context whose amount is 2 and expects two stacks.
 */
public class SparklePerPointTest {
    private static final int SPARKLE = 1306;
    private static final int LEVEL = 80;

    private static int phantasmAfter(int pointsSpent) {
        Character c = CharacterFactory.create(SPARKLE, LEVEL, true, null, null);
        Enemy e = EnemyFactory.create(1002011, 90, 1);
        Battle b = new Battle(List.of(c), List.of(e), new Random(0));
        b.startBattle();
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(c, c, e, 1, pointsSpent, null, b,
                SkillCategory.UNSPECIFIED);
        var rules = TriggerTables.of(SPARKLE).rulesFor(TriggerEvent.SKILL_POINT_SPENT).stream()
                .filter(r -> "talent_phantasm_stack".equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size(), "the stack rule must exist");
        TriggerInterpreter.apply(b, rules.getFirst(), ctx);
        return c.getBuffManager().stacksOf("幻相");
    }

    @Test
    public void twoSpentPointsGrantTwoStacks() {
        Assertions.assertEquals(1, phantasmAfter(1), "one point is one stack");
        Assertions.assertEquals(2, phantasmAfter(2),
                "two points must be two stacks -- that is what per-point means, and a per-action reading gives 1");
        System.out.println("[1306] per-point ok: 1 point -> " + phantasmAfter(1) + ", 2 points -> " + phantasmAfter(2));
    }
}
'''
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
eng_saved = io.open(WORK + '/' + ENG, encoding='utf-8').read()
print('judge written')


def focused():
    for p in __import__('glob').glob(WORK + '/build/test-results/test/*.xml'):
        __import__('os').remove(p)
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.SparklePerPointTest',
                        '--console=plain'], cwd=WORK, capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    import glob
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
                    if line.strip().startswith('[1306]'):
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
    __import__('os').remove(WORK + '/' + JREL)
    print('ROLLED BACK the judge')
    sys.exit(1)

mut_old = 'if ("event_amount".equals(String.valueOf(effect.getScale()).trim())) {'
print('mutation anchor count: %d' % eng_saved.count(mut_old))
if eng_saved.count(mut_old) != 1:
    print('REFUSING: mutation anchor not unique')
    sys.exit(1)
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(
    eng_saved.replace(mut_old, 'if (false) {  // MUTATION', 1))
_, reds2, msgs2, _, _ = focused()
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(eng_saved)
print('engine restored; the event_amount mutation is %s (reds=%s)' % ('GREEN (blind!)' if reds2 == 0 else 'red', reds2))
if reds2 == 0:
    for m in msgs2[:2]:
        print('  why: ' + m)
    __import__('os').remove(WORK + '/' + JREL)
    print('ROLLED BACK')
    sys.exit(1)

suite = subprocess.run([r'.\gradlew.bat', 'test', '--rerun-tasks', '--quiet', '--console=plain'], cwd=WORK,
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    __import__('os').remove(WORK + '/' + JREL)
    print('ROLLED BACK (suite red)')
    sys.exit(1)
gates = [subprocess.run([r'.\gradlew.bat', 'run', *a, '--quiet', '--console=plain'], cwd=WORK,
                        capture_output=True, text=True).returncode for a in ([], ['--args=mechanics'])]
print('gates: %s' % gates)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'test: 1306 phantasm stacks follow the spent points, with an engine mutation'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
