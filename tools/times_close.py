# -*- coding: utf-8 -*-
"""Round 631: close the times capability -- fixture, judge, and the engine mutation.

Round 630 failed with "Trigger recursion exceeded 8 levels while firing DEALING_DAMAGE": the fixture listened on
DEALING_DAMAGE while its own DAMAGE effect fires that same event. The shipped 8005 rule avoids this with the
damage_is_attack guard, but a fixture has no Damage instance in its hand-built context, so the cleaner fix is to
listen on BATTLE_START -- an event the settlement does not re-emit. Tier key "1" also becomes "2" (a relic declares 2
and 4).
"""

import io
import json
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
FX = 'src/test/resources/relic_sets/99004.json'
JREL = 'src/test/java/com/laosun/aluminium/test/TimesRepeatTest.java'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'


def rule(rid, times, note):
    return {'on': 'BATTLE_START', 'id': rid,
            'do': [{'op': 'DAMAGE', 'scale': 'self_attr:ATTACK', 'percent': 1.0,
                    'element': 'Physical', 'target': 'random_enemy', 'times': times}],
            'note': note}


doc = {'2': [
    rule('times_three', 3, '测试夹具：整次结算重复 3 遍（每次重抽随机敌方目标）。⚠ 事件用 BATTLE_START 而不是 '
                           'DEALING_DAMAGE —— 后者会被本效果自己再次触发（第 630 轮实测：递归超限）。'),
    rule('times_one', 1, '测试夹具：只重复 1 遍，作为比值基线。⚠ 除 times 外与 times_three 逐字相同。'),
]}
text = json.dumps(doc, ensure_ascii=False, indent=2) + '\n'
json.loads(text)
io.open(WORK + '/' + FX, 'w', encoding='utf-8', newline='').write(text)
print('fixture rewritten: tier key "2", both rules on BATTLE_START')

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
 * Effect-level repetition (2026-09-30): the {@code times} field.
 *
 * <p>The fixture set 99004 holds two rules that differ in nothing but {@code times} (3 vs 1), so the observable is a
 * RATIO: an absolute damage figure would drag defence and mitigation into the assertion. Both runs use the same
 * seed. The fixture listens on BATTLE_START because DEALING_DAMAGE is re-emitted by its own DAMAGE effect -- measured
 * in round 630 as "Trigger recursion exceeded 8 levels".
 *
 * <p>Nine shipped clauses are registered on this shape; 8005 is the one that has shipped (its note still owes the
 * 8006 mirror).
 */
public class TimesRepeatTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;

    private static double lossWith(String ruleId) {
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
eng_orig = io.open(WORK + '/' + ENG, encoding='utf-8').read()


def focused():
    return subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.TimesRepeatTest',
                           '--console=plain'], cwd=WORK, capture_output=True, text=True,
                          encoding='utf-8', errors='replace')


def reds():
    import glob
    import xml.etree.ElementTree as E
    n = 0
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        try:
            root = E.parse(p).getroot()
        except Exception:
            continue
        n += sum(1 for c in root.iter('testcase')
                 if c.find('failure') is not None or c.find('error') is not None)
    return n


r = focused()
print('focused exit: %d (reds=%d)' % (r.returncode, reds()))
if r.returncode != 0:
    import glob
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
                    print('  XMLFAIL %s' % (m.get('message') or '')[:250])
    print('NOT rolled back automatically')
    sys.exit(1)

# ENGINE mutation: make times always 1. The ratio must collapse, so the judge must go red.
mut_old = 'int times = effect.getTimes() == null ? 1 : effect.getTimes();'
mut_new = 'int times = 1;  // MUTATION'
print('mutation anchor count: %d' % eng_orig.count(mut_old))
if eng_orig.count(mut_old) != 1:
    print('REFUSING: mutation anchor not unique')
    sys.exit(1)
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(eng_orig.replace(mut_old, mut_new, 1))
focused()
print('engine mutated (times -> 1): reds=%d' % reds())
blind = reds() == 0
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(eng_orig)
print('engine restored; the mutation is %s' % ('GREEN (blind!)' if blind else 'red'))
if blind:
    sys.exit(1)
suite = subprocess.run([r'.\gradlew.bat', 'test', '--rerun-tasks', '--quiet', '--console=plain'], cwd=WORK,
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    sys.exit(1)
gates = [subprocess.run([r'.\gradlew.bat', 'run', *a, '--quiet', '--console=plain'], cwd=WORK,
                        capture_output=True, text=True).returncode for a in ([], ['--args=mechanics'])]
print('gates: %s' % gates)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'test: judge effect-level repetition by the settlement ratio, with an engine mutation'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
