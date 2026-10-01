# -*- coding: utf-8 -*-
"""Round 674: make GAIN_ENERGY honour amount_from_event, then prove it with the judge round 673 wrote.

Round 673 established that round 672's content change was a NO-OP: scaledAmount ignores amount_from_event, so a spend
of two points still paid 2 energy. The engine's getAmountFromEvent case lives in gainResource (line 1124) and
gainEnergyFor never looked at it. The anchor below is the exact text of gainEnergyFor's head, read in round 671.
Everything rolls back on any failure, engine included.
"""

import glob
import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
ENG = 'src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java'
JREL = 'src/test/java/com/laosun/aluminium/test/MishaPerPointTest.java'

OLD = '''    private static void gainEnergyFor(Battle battle, EffectSpec effect, TriggerContext ctx, CanHit target) {
        if (effect.getScale() == null || effect.getScale().isBlank()) {'''
NEW = '''    private static void gainEnergyFor(Battle battle, EffectSpec effect, TriggerContext ctx, CanHit target) {
        // ⭐ The event's own magnitude, mirroring gainResource (2026-09-30; reader: 1312's 「每消耗 1 个战技点…恢复
        // 2.00 点能量」). Without this branch the field was accepted at load time but ignored here, so a spend of two
        // points still paid the literal amount -- measured in round 673 by a judge the shipped tests could not replace.
        if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {
            double share = effect.getAmountPercent() == null ? 1 : effect.getAmountPercent();
            battle.grantEnergy(target, Math.round(ctx.amount() * share));
            return;
        }
        if (effect.getScale() == null || effect.getScale().isBlank()) {'''

# ⭐ The key: EffectSpec has NO @SerializedName for this field, so Gson maps it by its JAVA name -- `amountFromEvent`.
# Round 668 wrote `amount_from_event`, which Gson silently drops, so the flag never lit and the change was a no-op that
# passed the load check, the full suite and both demo gates. Measured in round 675/676.
CHAR = 'src/main/resources/characters/1312.json'
cdoc = json.load(io.open(WORK + '/' + CHAR, encoding='utf-8'))
cwhere = cdoc if isinstance(cdoc, list) else cdoc.get('rules')
crule = next((r for r in cwhere if isinstance(r, dict)
              and r.get('id') == 'talent_energy_on_skill_point_spent'), None)
if crule is None:
    print('REFUSING: 1312 rule not found')
    sys.exit(1)
centry = crule['do'][0]
print('1312 before: %s' % json.dumps(centry, ensure_ascii=False))
centry.pop('amount_from_event', None)
centry['amountFromEvent'] = True
ctxt = json.dumps(cdoc, ensure_ascii=False, indent=2) + '\n'
json.loads(ctxt)
io.open(WORK + '/' + CHAR, 'w', encoding='utf-8', newline='').write(ctxt)
print('1312 after:  %s' % json.dumps(centry, ensure_ascii=False))

eng_saved = io.open(WORK + '/' + ENG, encoding='utf-8').read()
print('engine anchor count: %d' % eng_saved.count(OLD))
if eng_saved.count(OLD) != 1:
    print('REFUSING: the gainEnergyFor anchor is not unique')
    sys.exit(1)

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
 * <p>The content says `amount_from_event: true` + `amount_percent: 2`, but on 2026-09-30 round 673 measured that
 * GAIN_ENERGY ignored the field, so the change was a no-op that the whole suite could not see (every shipped judge
 * spends one point per call, where both readings agree). gainEnergyFor now reads it, mirroring gainResource.
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


def bail(msg):
    io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(eng_saved)
    if os.path.exists(WORK + '/' + JREL):
        os.remove(WORK + '/' + JREL)
    print('ROLLED BACK (%s)' % msg)
    sys.exit(1)


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


io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(eng_saved.replace(OLD, NEW, 1))
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
print('engine patched and judge written')
code, reds, msgs, printed, out = focused()
print('focused exit: %d (reds=%d)' % (code, reds))
for l in printed[:2]:
    print('  ' + l[:170])
if code != 0:
    for m in msgs[:3]:
        print('  XMLFAIL ' + m)
    for l in out.strip().split('\n')[-6:]:
        print('  RAW ' + l.strip()[:150])
    bail('the judge is red with the engine patched')

mut_old = 'if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {\n            double share = effect.getAmountPercent()'
patched = io.open(WORK + '/' + ENG, encoding='utf-8').read()
print('mutation anchor count: %d' % patched.count(mut_old))
if patched.count(mut_old) != 1:
    bail('mutation anchor not unique')
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(
    patched.replace(mut_old, 'if (false) {\n            double share = effect.getAmountPercent()', 1))
_, reds2, msgs2, _, _ = focused()
io.open(WORK + '/' + ENG, 'w', encoding='utf-8', newline='').write(patched)
print('mutation (the new branch off) is %s (reds=%s)' % ('GREEN (blind!)' if reds2 == 0 else 'red', reds2))
if reds2 == 0:
    bail('the mutation is invisible')

suite = subprocess.run([r'.\gradlew.bat', 'test', '--rerun-tasks', '--quiet', '--console=plain'], cwd=WORK,
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    bail('suite red')
gates = [subprocess.run([r'.\gradlew.bat', 'run', *a, '--quiet', '--console=plain'], cwd=WORK,
                        capture_output=True, text=True).returncode for a in ([], ['--args=mechanics'])]
print('gates: %s' % gates)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'feat: GAIN_ENERGY reads amount_from_event, so 1312 pays per spent point'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
