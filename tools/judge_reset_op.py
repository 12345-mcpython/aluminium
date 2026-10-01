# -*- coding: utf-8 -*-
"""Round 733: the judge and mutation for RESET_TRIGGER_LIMIT.

Two hand-built rules (TriggerSpecs is a generic reflective setter, and it fails loudly on a wrong field name):
A = SKILL_CAST -> ADD_STACK probe, with the per-turn limiter at 1; B = ULT_CAST -> RESET_TRIGGER_LIMIT on A's id.
Fire A twice (the second is blocked), fire B, fire A again -- the third one must land.
"""

import glob
import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CANHIT = 'src/main/java/com/laosun/aluminium/models/CanHit.java'
JREL = 'src/test/java/com/laosun/aluminium/test/ResetTriggerLimitTest.java'
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
        + ' * RESET_TRIGGER_LIMIT (2026-09-30; readers 1305, 1207, 1403): 「\u65bd\u653e\u7ec8\u7ed3\u6280\u540e\u91cd\u7f6e\u8be5\u6548\u679c\u89e6\u53d1\u6b21\u6570\u300d.' + NL
        + ' *' + NL
        + ' * <p>One rule is capped at one firing per turn; a second rule on ULT_CAST clears exactly that rule limit, so' + NL
        + ' * the capped rule fires again. ⚠ resetTriggerLimits() would clear EVERY rule of the unit instead.' + NL
        + ' */' + NL
        + 'public class ResetTriggerLimitTest {' + NL
        + '    private static final int OWNER = 1001;' + NL
        + '    private static final int LEVEL = 80;' + NL
        + '    private static final String CAPPED = ' + Q + 'limit_probe' + Q + ';' + NL + NL
        + '    private static int probeStacksAfterTwoFiresThenReset() {' + NL
        + '        Character owner = CharacterFactory.create(OWNER, LEVEL);' + NL
        + '        Enemy enemy = EnemyFactory.create(1002011, 90, 1);' + NL
        + '        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));' + NL
        + '        battle.startBattle();' + NL + NL
        + '        EffectSpec stack = new EffectSpec();' + NL
        + '        TriggerSpecs.set(stack, ' + Q + 'op' + Q + ', ' + Q + 'ADD_STACK' + Q + ');' + NL
        + '        TriggerSpecs.set(stack, ' + Q + 'buff' + Q + ', ' + Q + '探针' + Q + ');' + NL
        + '        TriggerSpecs.set(stack, ' + Q + 'amount' + Q + ', 1.0d);' + NL
        + '        TriggerSpecs.set(stack, ' + Q + 'target' + Q + ', ' + Q + 'self' + Q + ');' + NL
        + '        TriggerSpec capped = TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of(), stack);' + NL
        + '        TriggerSpecs.set(capped, ' + Q + 'id' + Q + ', CAPPED);' + NL
        + '        TriggerSpecs.set(capped, ' + Q + 'perTurn' + Q + ', 1);' + NL + NL
        + '        EffectSpec reset = new EffectSpec();' + NL
        + '        TriggerSpecs.set(reset, ' + Q + 'op' + Q + ', ' + Q + 'RESET_TRIGGER_LIMIT' + Q + ');' + NL
        + '        TriggerSpecs.set(reset, ' + Q + 'rule' + Q + ', CAPPED);' + NL
        + '        TriggerSpecs.set(reset, ' + Q + 'target' + Q + ', ' + Q + 'self' + Q + ');' + NL
        + '        TriggerSpec clear = TriggerSpecs.rule(TriggerEvent.ULT_CAST.name(), List.of(), reset);' + NL + NL
        + '        owner.setTriggerTable(new TriggerTable(OWNER, List.of(capped, clear)));' + NL
        + '        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, enemy, 0, 0);' + NL
        + '        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, enemy, 0, 0);' + NL
        + '        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, enemy, 0, 0);' + NL
        + '        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, enemy, 0, 0);' + NL
        + '        return owner.getBuffManager().stacksOf(' + Q + '探针' + Q + ');' + NL
        + '    }' + NL + NL
        + '    @Test' + NL
        + '    public void theResetLetsTheCappedRuleFireAgain() {' + NL
        + '        int stacks = probeStacksAfterTwoFiresThenReset();' + NL
        + '        Assertions.assertEquals(2, stacks,' + NL
        + '                ' + Q + 'two fires before the reset (the second is capped) plus one after must be 2, got ' + Q + ' + stacks);' + NL
        + '        System.out.println(' + Q + '[reset] ok: stacks=' + Q + ' + stacks);' + NL
        + '    }' + NL + '}' + NL)
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
saved = io.open(WORK + '/' + CANHIT, encoding='utf-8').read()
print('judge written')


def bail(msg):
    if os.path.exists(WORK + '/' + JREL):
        os.remove(WORK + '/' + JREL)
    subprocess.run(['git', 'checkout', '--', CANHIT], cwd=WORK)
    print('ROLLED BACK (%s)' % msg)
    sys.exit(1)


def focused():
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        os.remove(p)
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.ResetTriggerLimitTest',
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
                    if ln.strip().startswith('[reset]'):
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

mut_old = '        triggerTurnUses.remove(key);'
print('mutation anchor: %d' % saved.count(mut_old))
if saved.count(mut_old) != 1:
    bail('the mutation anchor is not unique')
io.open(WORK + '/' + CANHIT, 'w', encoding='utf-8', newline='').write(
    saved.replace(mut_old, '        // MUTATION: the per-turn counter is not cleared', 1))
_, r2, _, _, _ = focused()
io.open(WORK + '/' + CANHIT, 'w', encoding='utf-8', newline='').write(saved)
print('mutation (the per-turn counter kept) is %s (reds=%s)' % ('GREEN (blind!)' if r2 == 0 else 'red', r2))
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
                      'test: judge RESET_TRIGGER_LIMIT by re-firing a capped rule, with an engine mutation'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
