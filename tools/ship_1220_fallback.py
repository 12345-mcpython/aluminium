# -*- coding: utf-8 -*-
"""Round 707: 1220 Feixiao -- the fallback reaches her two follow-ups, with a judge and an engine mutation.

Pre-flight (rounds 705-706): six test files touch 1220; five pin conditions, lists or numbers and are transparent to a
`target` change, and the sixth (FeixiaoTest) fires at one LIVE enemy, where the fallback and the preference pick the
same unit. Its own note already names the fallback clause as the real blocker (corrected in round 615).
"""

import glob
import io
import json
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
CHAR = 'src/main/resources/characters/1220.json'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
JREL = 'src/test/java/com/laosun/aluminium/test/FeixiaoFallbackTest.java'
Q, NL = chr(34), chr(10)
RIDS = ('talent_followup_on_teammate_attack', 'skill_repeats_the_talent_followup')

doc = json.load(io.open(WORK + '/' + CHAR, encoding='utf-8'))
rules = doc if isinstance(doc, list) else doc.get('rules')
changed = 0
for rid in RIDS:
    rule = next((r for r in rules if isinstance(r, dict) and r.get('id') == rid), None)
    if rule is None:
        print('REFUSING: %s not found' % rid)
        sys.exit(1)
    hits = [e for e in (rule.get('do') or []) if isinstance(e, dict) and e.get('target') == 'target']
    if len(hits) != 1:
        print('REFUSING: %s has %d effects targeting the event target' % (rid, len(hits)))
        sys.exit(1)
    hits[0]['target'] = 'target_else_random_enemy'
    changed += 1
    rule['note'] = (str(rule.get('note') or '') + ' ⭐ **退路已出货** ✓（2026-09-30 ✓）：⚠ 本条 `target` 由 '
                    '`target` ✗ 改为 **`target_else_random_enemy`** ✗ ⇒ ⚠ 即 ⚠ 事件目标**未阵亡** ⇒ 打它 ✓；'
                    '⚠ 已阵亡 ⇒ ⚠ 打一个存活的随机敌方 ✓（⚠ 与其注记里那句「若不存在可攻击的主目标，则攻击敌方'
                    '随机单体」逐字对齐 ✓）。⚠ 引擎侧见 `TriggerInterpreter.resolveTarget` ✓（⚠ 谓词 '
                    '`CanHit.isDeath()` ✓）；⚠ **窗口真实存在**：⚠ `CanHit` 第 550 行 `isDeath()` alone does not '
                    'remove anybody ✗ ⇒ ⚠ 已阵亡者仍留在 `battle.enemies` ✓。').strip()
print('1220 effects retargeted: %d' % changed)
text = json.dumps(doc, ensure_ascii=False, indent=2) + NL
json.loads(text)
io.open(WORK + '/' + CHAR, 'w', encoding='utf-8', newline='').write(text)

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
        + ' * 1220 Feixiao: 「若不存在可攻击的主目标，则攻击敌方随机单体」 -- her follow-up fallback.' + NL
        + ' *' + NL
        + ' * <p>Two enemies, because with one the fallback and the preference pick the same unit. A is defeated first' + NL
        + ' * with CanHit.perish(), which fires no events and leaves it in Battle.enemies.' + NL
        + ' */' + NL
        + 'public class FeixiaoFallbackTest {' + NL
        + '    private static final int FEIXIAO = 1220;' + NL
        + '    private static final int ALLY = 1002;' + NL
        + '    private static final int LEVEL = 80;' + NL + NL
        + '    private static double[] lossesWithDeadTarget() {' + NL
        + '        Character feixiao = CharacterFactory.create(FEIXIAO, LEVEL);' + NL
        + '        Character ally = CharacterFactory.create(ALLY, LEVEL);' + NL
        + '        Enemy a = EnemyFactory.create(1002011, 90, 1);' + NL
        + '        Enemy b = EnemyFactory.create(1002011, 90, 2);' + NL
        + '        Battle battle = new Battle(List.of(feixiao, ally), List.of(a, b), new Random(0));' + NL
        + '        battle.startBattle();' + NL
        + '        a.perish();' + NL
        + '        Assertions.assertTrue(a.isDeath() && battle.enemies.contains(a),' + NL
        + '                ' + Q + 'the fixture must leave A defeated but still in the roster' + Q + ');' + NL
        + '        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(feixiao, ally, a, 1, 0, null, battle,' + NL
        + '                SkillCategory.UNSPECIFIED);' + NL
        + '        var rules = TriggerTables.of(FEIXIAO).rulesFor(TriggerEvent.ALLY_ATTACK).stream()' + NL
        + '                .filter(r -> ' + Q + 'talent_followup_on_teammate_attack' + Q + '.equals(r.id())).toList();' + NL
        + '        Assertions.assertEquals(1, rules.size(), ' + Q + 'the follow-up rule must exist' + Q + ');' + NL
        + '        double aBefore = a.getCurrentHp();' + NL
        + '        double bBefore = b.getCurrentHp();' + NL
        + '        TriggerInterpreter.apply(battle, rules.getFirst(), ctx);' + NL
        + '        return new double[]{aBefore - a.getCurrentHp(), bBefore - b.getCurrentHp()};' + NL
        + '    }' + NL + NL
        + '    @Test' + NL
        + '    public void aDeadMainTargetFallsBackToARandomEnemy() {' + NL
        + '        double[] loss = lossesWithDeadTarget();' + NL
        + '        Assertions.assertEquals(0.0, loss[0], 1e-9, ' + Q + 'the defeated main target must not be hit' + Q + ');' + NL
        + '        Assertions.assertTrue(loss[1] > 0,' + NL
        + '                ' + Q + 'the fallback must reach another enemy, got ' + Q + ' + loss[1]);' + NL
        + '        System.out.println(' + Q + '[1220] fallback ok: deadTargetLoss=' + Q + ' + loss[0] + ' + Q + ' otherLoss=' + Q + ' + loss[1]);' + NL
        + '    }' + NL + '}' + NL)
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
eng_saved = io.open(WORK + '/' + ENG, encoding='utf-8').read()
print('judge written')


def bail(msg):
    if os.path.exists(WORK + '/' + JREL):
        os.remove(WORK + '/' + JREL)
    subprocess.run(['git', 'checkout', '--', CHAR, ENG], cwd=WORK)
    print('ROLLED BACK (%s)' % msg)
    sys.exit(1)


def focused():
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        os.remove(p)
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.FeixiaoFallbackTest',
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
                    if ln.strip().startswith('[1220]'):
                        printed.append(ln.strip())
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    reds += 1
                    msgs.append((m.get('message') or '')[:220])
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
                      'content: 1220 the two follow-ups fall back to a random enemy when the main target is gone'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
