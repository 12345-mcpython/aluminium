"""Re-apply the channel, and read the panels as a TRUE delta (round 11 of the goal).

Two rounds were spent on a false trail. What settled it:
  * `DebuffClass.fromString` is correct (a loop over `values()` with an equality check, null for anything else);
  * the parse point is correct -- `resource_changed` is the FIRST check in `parseCondition` (verified at L1176-1182) and the new matcher
    was inserted before it;
  * and the number that looked like "the dot rule also fired" -- DEFENCE +49.61 -- is the SAME number an unfiltered rule produced in
    the pipe probe, i.e. it is the constant gap between `baseValue()` and `get()` (relics, cones, the level convention), not a rule's
    contribution. The panels judge compared `get()` against `baseValue()`, which reads that gap as a gain.

So the reading becomes a real delta: snapshot before `startBattle()` and after.
"""
import io
import subprocess
import sys

out = subprocess.run([sys.executable, "tools/ship_debuff_class.py"], capture_output=True, text=True)
print(out.stdout.strip()[-200:])

JUDGE = "src/test/java/com/laosun/aluminium/test/DebuffClassConditionTest.java"
io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「敌方对我方施加了**控制类**负面状态」可以被问了 (2026-10-02).
 *
 * <p>ONE landed control, TWO watchers: the rule asking for 「控制类」 must move ATTACK, and the one asking for 「持续伤害类」 must leave
 * DEFENCE alone. The first is also the proof that the event happened at all, which is what gives the second its meaning.
 *
 * <p>⚠ Both readings are DELTAS across `startBattle()`: an earlier version compared `get()` with `baseValue()` and read the constant
 * gap between them (relics, cones, the level convention) as a rule's contribution -- measured, the same +49.61 appears in a scene
 * whose only DEBUFF_APPLIED rule was unfiltered, which is what exposed it.
 */
public class DebuffClassConditionTest {
    private static final int OWNER = 1002;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;

    /** The control rule fires; the dot rule does not. */
    @Test
    public void theFilterTellsTheTwoFamiliesApart() {
        double[] gains = run();
        System.out.println("[debuff-class] control-watch ATTACK gain = " + gains[0]
                + " ; dot-watch DEFENCE gain = " + gains[1]);

        Assertions.assertTrue(gains[0] > EPS,
                "「控制类」-- the rule that asked for it fired, so the event really happened");
        Assertions.assertEquals(0.0, gains[1], EPS,
                "「持续伤害类」-- the same event does NOT fire the rule that asked for the other family");
    }

    /** { the control-watch's ATTACK gain, the dot-watch's DEFENCE gain } across startBattle(). */
    private static double[] run() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);

        EffectSpec land = new EffectSpec();
        TriggerSpecs.set(land, "op", "APPLY_CONTROL");
        // ⚠ The keys the validator names: `APPLY_CONTROL requires "control"` and `requires "turns"`.
        TriggerSpecs.set(land, "control", "\\u51bb\\u7ed3");
        TriggerSpecs.set(land, "turns", 2);
        TriggerSpecs.set(land, "baseChance", 1.0);
        TriggerSpecs.set(land, "target", "all_enemies");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), land),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of("debuff_class:control"), boost("ATTACK")),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of("debuff_class:dot"), boost("DEFENCE"))),
                List.of()));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        double attackBefore = owner.getAttribute(AttributeType.ATTACK).get();
        double defenceBefore = owner.getAttribute(AttributeType.DEFENCE).get();
        battle.startBattle();
        battle.processRequests();
        return new double[] {
                owner.getAttribute(AttributeType.ATTACK).get() - attackBefore,
                owner.getAttribute(AttributeType.DEFENCE).get() - defenceBefore};
    }

    /** A rule effect that leaves a mark on a panel, so the reading needs no declared resource. */
    private static EffectSpec boost(String attribute) {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", "MODIFY_ATTR");
        TriggerSpecs.set(spec, "attribute", attribute);
        TriggerSpecs.set(spec, "percent", 0.5);
        TriggerSpecs.set(spec, "permanent", Boolean.TRUE);
        TriggerSpecs.set(spec, "target", "self");
        return spec;
    }
}
''')
print("ok   the judge reads a true delta across startBattle")
