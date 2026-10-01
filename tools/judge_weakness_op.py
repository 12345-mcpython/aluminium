# -*- coding: utf-8 -*-
"""Round 723: the weakness op's judge and mutation, plus removing the wrapper script proven broken in round 721.

The reader (1315) is already registered, so the engine can be judged against a hand-built EffectSpec -- the pattern
DrRatioTest uses for its -30% reference (TriggerSpecs.set + TriggerSpecs.rule + a fresh TriggerTable).
"""

import glob
import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENEMY = 'src/main/java/com/laosun/aluminium/models/enemy/Enemy.java'
JREL = 'src/test/java/com/laosun/aluminium/test/WeaknessOpTest.java'
STALE = 'tools/add_weakness_op.py'
Q, NL = chr(34), chr(10)

JAVA = ('package com.laosun.aluminium.test;' + NL + NL
        + 'import com.laosun.aluminium.Battle;' + NL
        + 'import com.laosun.aluminium.beans.EffectSpec;' + NL
        + 'import com.laosun.aluminium.enums.DamageElement;' + NL
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
        + ' * ADD_ELEMENTAL_WEAKNESS (2026-09-30; readers 1315 and 1310, both registered):' + NL
        + ' * 「\u4e3a\u6307\u5b9a\u654c\u65b9\u5355\u4f53\u6dfb\u52a0\u7269\u7406\u5f31\u70b9\u300d.' + NL
        + ' *' + NL
        + ' * <p>isWeakTo is both the engine judgement point and a ready-made observable, so no two-enemy or hand-read' + NL
        + ' * trickery is needed: false before, true after. The rule is built by hand because the reader has not shipped.' + NL
        + ' */' + NL
        + 'public class WeaknessOpTest {' + NL
        + '    private static final int CASTER = 1315;' + NL
        + '    private static final int LEVEL = 80;' + NL + NL
        + '    private static boolean weakAfter(DamageElement element) {' + NL
        + '        Character caster = CharacterFactory.create(CASTER, LEVEL);' + NL
        + '        Enemy enemy = EnemyFactory.create(1002011, 90, 1);' + NL
        + '        Battle battle = new Battle(List.of(caster), List.of(enemy), new Random(0));' + NL
        + '        battle.startBattle();' + NL
        + '        EffectSpec effect = new EffectSpec();' + NL
        + '        TriggerSpecs.set(effect, ' + Q + 'op' + Q + ', ' + Q + 'ADD_ELEMENTAL_WEAKNESS' + Q + ');' + NL
        + '        TriggerSpecs.set(effect, ' + Q + 'element' + Q + ', element.name());' + NL
        + '        TriggerSpecs.set(effect, ' + Q + 'target' + Q + ', ' + Q + 'target' + Q + ');' + NL
        + '        caster.setTriggerTable(new TriggerTable(CASTER, List.of(TriggerSpecs.rule(' + NL
        + '                TriggerEvent.BATTLE_START.name(), List.of(), effect))));' + NL
        + '        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(caster, caster, enemy, 1, 0, null,' + NL
        + '                battle, com.laosun.aluminium.enums.SkillCategory.UNSPECIFIED);' + NL
        + '        TriggerInterpreter.apply(battle, caster.getTriggerTable().rulesFor(TriggerEvent.BATTLE_START).getFirst(), ctx);' + NL
        + '        return enemy.isWeakTo(element);' + NL
        + '    }' + NL + NL
        + '    @Test' + NL
        + '    public void theOpAddsTheNamedElement() {' + NL
        + '        Assertions.assertFalse(WeaknessOpTest.probeIsWeakToBefore(DamageElement.PHYSICAL),' + NL
        + '                ' + Q + 'the fixture enemy must not already be weak to it' + Q + ');' + NL
        + '        Assertions.assertTrue(weakAfter(DamageElement.PHYSICAL),' + NL
        + '                ' + Q + 'the op must add the weakness, and isWeakTo must then see it' + Q + ');' + NL
        + '        System.out.println(' + Q + '[weakness] ok: PHYSICAL before=false after=' + Q + ' + weakAfter(DamageElement.PHYSICAL));' + NL
        + '    }' + NL + NL
        + '    private static boolean probeIsWeakToBefore(DamageElement element) {' + NL
        + '        Enemy enemy = EnemyFactory.create(1002011, 90, 1);' + NL
        + '        return enemy.isWeakTo(element);' + NL
        + '    }' + NL + '}' + NL)
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
saved_enemy = io.open(WORK + '/' + ENEMY, encoding='utf-8').read()
stale_existed = os.path.exists(WORK + '/' + STALE)
print('judge written; stale script present: %s' % stale_existed)


def bail(msg):
    if os.path.exists(WORK + '/' + JREL):
        os.remove(WORK + '/' + JREL)
    subprocess.run(['git', 'checkout', '--', ENEMY], cwd=WORK)
    print('ROLLED BACK (%s)' % msg)
    sys.exit(1)


def focused():
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        os.remove(p)
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.WeaknessOpTest',
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
                    if ln.strip().startswith('[weakness]'):
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
    for l in out.strip().split(NL):
        if 'error:' in l or '错误' in l or '.java:' in l:
            print('  DIAG ' + l.strip()[:190])
    bail('the judge is red')

mut_old = 'widened.add(element);'
print('mutation anchor: %d' % saved_enemy.count(mut_old))
if saved_enemy.count(mut_old) != 1:
    bail('the mutation anchor is not unique')
io.open(WORK + '/' + ENEMY, 'w', encoding='utf-8', newline='').write(
    saved_enemy.replace(mut_old, '// MUTATION: the element is never added', 1))
_, r2, _, _, _ = focused()
io.open(WORK + '/' + ENEMY, 'w', encoding='utf-8', newline='').write(saved_enemy)
print('mutation (the element never added) is %s (reds=%s)' % ('GREEN (blind!)' if r2 == 0 else 'red', r2))
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
if stale_existed:
    os.remove(WORK + '/' + STALE)
    print('removed the wrapper script that failed in round 721 while the same edits passed inline')
subprocess.run(['git', 'add', '-A', 'src', 'tools'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'test: judge ADD_ELEMENTAL_WEAKNESS via isWeakTo, with an engine mutation'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
