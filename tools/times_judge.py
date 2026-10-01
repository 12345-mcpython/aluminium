# -*- coding: utf-8 -*-
"""Round 630: the times judge -- and a fixture fix, because a relic tier key must be 2, not 1.

`RelicTriggerTables.of(set).at(pieces)` validates the key against the set's DECLARED tiers, and a relic declares 2
and 4. The fixture written last round used key "1", which the loader would refuse; it becomes "2" and the judge asks
for at(2).
"""

import io
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
FX = 'src/test/resources/relic_sets/99004.json'
JREL = 'src/test/java/com/laosun/aluminium/test/TimesRepeatTest.java'

# ---- fix the tier key ----------------------------------------------------------------------------------------
path = WORK + '/' + FX
doc = json.load(io.open(path, encoding='utf-8'))
if '1' in doc and '2' not in doc:
    doc['2'] = doc.pop('1')
    io.open(path, 'w', encoding='utf-8', newline='').write(json.dumps(doc, ensure_ascii=False, indent=2) + '\n')
    print('fixture tier key: "1" -> "2" (a relic declares 2 and 4)')
else:
    print('fixture keys: %s' % sorted(doc.keys()))

JAVA = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerInterpreter;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Effect-level repetition (2026-09-30): the {@code times} field on an effect.
 *
 * <p>The fixture set 99004 carries two rules that differ in NOTHING but {@code times} (3 vs 1), so the observable is
 * a RATIO -- an absolute damage figure would drag defence and mitigation into the assertion. Both battles use the
 * same seed, so the two runs see the same rolls.
 *
 * <p>⚠ Twelve shipped clauses are registered on this shape (「N 次伤害，每次对随机敌方单体」): 1009, 1214, 1302, 1312,
 * 1513, 1505, 1510, 8005, 1221 -- 8005 is the one that has shipped, and its own note still owes the 8006 mirror.
 */
public class TimesRepeatTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int STAR = 5;

    private static Character wearer() {
        return CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(99004, STAR, 15));
    }

    /** Applies the named rule of 99004 to one enemy and returns how much HP it lost. */
    private static double lossWith(String ruleId) {
        Character c = wearer();
        Enemy e = EnemyFactory.create(1002011, 90, 1);
        Battle b = new Battle(List.of(c), List.of(e), new Random(0));
        b.startBattle();
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(c, c, e, 1, 0, null, b,
                SkillCategory.UNSPECIFIED);
        var mine = b == null ? null : c.getTriggerTable() == null ? null : null;
        var rules = RelicTriggerTables.of(99004).at(2)
                .matching(TriggerEvent.DEALING_DAMAGE, ctx).stream()
                .filter(r -> ruleId.equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size(), ruleId + " must match");
        double before = e.getCurrentHp();
        TriggerInterpreter.apply(b, rules.getFirst(), ctx);
        return before - e.getCurrentHp();
    }

    @Test
    public void threeSettlementsDealThreeTimes() {
        double one = lossWith("times_one");
        double three = lossWith("times_three");
        Assertions.assertTrue(one > 0, "the baseline must deal something (got " + one + ")");
        Assertions.assertEquals(3.0 * one, three, 1e-6,
                "times: 3 must settle three times what times: 1 settles (one=" + one + ", three=" + three + ")");
        System.out.println("[times] ratio ok: one=" + one + " three=" + three);
    }
}
'''
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
print('judge written')


def run(*a):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + ['--quiet', '--console=plain'], cwd=WORK,
                          capture_output=True, text=True, encoding='utf-8', errors='replace')


res = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.TimesRepeatTest',
                      '--console=plain'], cwd=WORK, capture_output=True, text=True,
                     encoding='utf-8', errors='replace')
out = ((res.stdout or '') + (res.stderr or ''))
print('focused exit: %d' % res.returncode)
if res.returncode != 0:
    for l in out.strip().split('\n')[-10:]:
        print('  RAW ' + l.strip()[:170])
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
                                                  (m.get('message') or '')[:250]))
    print('NOT rolled back automatically')
    sys.exit(1)
suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'test: judge effect-level repetition by the settlement ratio (times 3 vs 1)'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
