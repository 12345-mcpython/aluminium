# -*- coding: utf-8 -*-
"""Round 765: per_subject's judge and mutation.

One hand-built rule with perTurn 1, a probe stack that CAN exceed one (round 747's lesson: addStack defaults the cap
to 1), and per_subject scoping the count to the triggerer. Two different teammates fire the same event; with the scope
both land, without it the second is blocked.
"""

import glob
import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
JREL = 'src/test/java/com/laosun/aluminium/test/PerSubjectLimitTest.java'
Q, NL = chr(34), chr(10)

JAVA = ('package com.laosun.aluminium.test;' + NL + NL
        + 'import com.laosun.aluminium.Battle;' + NL
        + 'import com.laosun.aluminium.beans.EffectSpec;' + NL
        + 'import com.laosun.aluminium.beans.TriggerSpec;' + NL
        + 'import com.laosun.aluminium.enums.TriggerEvent;' + NL
        + 'import com.laosun.aluminium.models.Character;' + NL
        + 'import com.laosun.aluminium.models.TriggerTable;' + NL
        + 'import com.laosun.aluminium.models.enemy.Enemy;' + NL
        + 'import com.laosun.aluminium.models.enemy.EnemyFactory;' + NL
        + 'import com.laosun.aluminium.utils.CharacterFactory;' + NL
        + 'import org.junit.jupiter.api.Assertions;' + NL
        + 'import org.junit.jupiter.api.Test;' + NL + NL
        + 'import java.util.List;' + NL + 'import java.util.Random;' + NL + NL
        + '/**' + NL
        + ' * 「该效果每个角色最多触发 1 次」 (1403) -- a firing count that belongs to the TRIGGERER, not the owner.' + NL
        + ' *' + NL
        + ' * <p>The probe stack must be allowed past one (addStack caps at 1 by default), otherwise the reading' + NL
        + ' * saturates and a working scope looks like a frozen failure -- the six rounds lost in 734-747.' + NL
        + ' */' + NL
        + 'public class PerSubjectLimitTest {' + NL
        + '    private static final int OWNER = 1001;' + NL
        + '    private static final int ALLY_A = 1002;' + NL
        + '    private static final int ALLY_B = 1004;' + NL
        + '    private static final int LEVEL = 80;' + NL + NL
        + '    private static int stacksAfterTwoDifferentTriggerers(String perSubject) {' + NL
        + '        Character owner = CharacterFactory.create(OWNER, LEVEL);' + NL
        + '        Character a = CharacterFactory.create(ALLY_A, LEVEL);' + NL
        + '        Character b = CharacterFactory.create(ALLY_B, LEVEL);' + NL
        + '        Enemy enemy = EnemyFactory.create(1002011, 90, 1);' + NL
        + '        Battle battle = new Battle(List.of(owner, a, b), List.of(enemy), new Random(0));' + NL
        + '        battle.startBattle();' + NL + NL
        + '        EffectSpec stack = new EffectSpec();' + NL
        + '        TriggerSpecs.set(stack, ' + Q + 'op' + Q + ', ' + Q + 'ADD_STACK' + Q + ');' + NL
        + '        TriggerSpecs.set(stack, ' + Q + 'buff' + Q + ', ' + Q + '探针' + Q + ');' + NL
        + '        TriggerSpecs.set(stack, ' + Q + 'amount' + Q + ', 1.0d);' + NL
        + '        TriggerSpecs.set(stack, ' + Q + 'target' + Q + ', ' + Q + 'self' + Q + ');' + NL
        + '        TriggerSpecs.set(stack, ' + Q + 'permanent' + Q + ', Boolean.TRUE);' + NL
        + '        TriggerSpecs.set(stack, ' + Q + 'maxStacks' + Q + ', 5);' + NL
        + '        TriggerSpec rule = TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of(), stack);' + NL
        + '        TriggerSpecs.set(rule, ' + Q + 'id' + Q + ', ' + Q + 'subject_probe' + Q + ');' + NL
        + '        TriggerSpecs.set(rule, ' + Q + 'perTurn' + Q + ', 1);' + NL
        + '        if (perSubject != null) {' + NL
        + '            TriggerSpecs.set(rule, ' + Q + 'perSubject' + Q + ', perSubject);' + NL
        + '        }' + NL
        + '        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));' + NL + NL
        + '        battle.fireTriggers(TriggerEvent.SKILL_CAST, a, enemy, 0, 0);' + NL
        + '        battle.fireTriggers(TriggerEvent.SKILL_CAST, b, enemy, 0, 0);' + NL
        + '        return owner.getBuffManager().stacksOf(' + Q + '探针' + Q + ');' + NL
        + '    }' + NL + NL
        + '    @Test' + NL
        + '    public void theCountFollowsTheTriggerer() {' + NL
        + '        Assertions.assertEquals(1, stacksAfterTwoDifferentTriggerers(null),' + NL
        + '                ' + Q + 'without the scope the second firer is blocked by the owner-wide count' + Q + ');' + NL
        + '        int scoped = stacksAfterTwoDifferentTriggerers(' + Q + 'actor' + Q + ');' + NL
        + '        Assertions.assertEquals(2, scoped,' + NL
        + '                ' + Q + 'with per_subject actor each triggerer gets its own count, got ' + Q + ' + scoped);' + NL
        + '        System.out.println(' + Q + '[subject] ok: owner-wide=' + Q + ' + stacksAfterTwoDifferentTriggerers(null)' + NL
        + '                + ' + Q + ' per-actor=' + Q + ' + scoped);' + NL
        + '    }' + NL + '}' + NL)
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
saved = io.open(WORK + '/' + ENG, encoding='utf-8').read()
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
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.PerSubjectLimitTest',
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
                    if ln.strip().startswith('[subject]'):
                        printed.append(ln.strip())
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    reds += 1
                    msgs.append((m.get('message') or '')[:260])
    return r.returncode, reds, msgs, printed, ((r.stdout or '') + (r.stderr or ''))


code, reds, msgs, printed, out = focused()
print('focused exit %d (reds %d)' % (code, reds))
for l in printed[:1]:
    print('  ' + l[:170])
if code != 0:
    for m in msgs[:3]:
        print('  XMLFAIL ' + m)
    for l in out.split(NL):
        if 'error:' in l or '错误' in l or '.java:' in l:
            print('  DIAG ' + l.strip()[:190])
    bail('the judge is red')

mut_old = '        return unit == null ? "" : "@" + System.identityHashCode(unit);'
print('mutation anchor: %d' % saved.count(mut_old))
if saved.count(mut_old) != 1:
    bail('the mutation anchor is not unique')
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(
    saved.replace(mut_old, '        return "";  // MUTATION: the scope is dropped', 1))
_, r2, _, _, _ = focused()
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(saved)
print('mutation (scope dropped) is %s (reds=%s)' % ('GREEN (blind!)' if r2 == 0 else 'red', r2))
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
                      'test: judge per-subject firing counts, with an engine mutation'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
