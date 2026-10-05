"""Re-apply the debuff-class channel, with a judge that reads panels instead of resources (round 10 of the goal).

Last round's failure was in the READING, not the channel: the probe asked a `ResourceSpec`-declared id (whose constructor args it
got wrong) and `hasState("冻结")` (which is not how that control shows up). The pipe probe settles it: a plain BATTLE_START rule moves
ATTACK by 273.42, and an unfiltered DEBUFF_APPLIED rule fired -- so the event does arrive.

So: the channel goes back in, and the reading watches two panels (ATTACK for the control-class rule, DEFENCE for the dot-class one).
Two different attributes on purpose: two modifiers on ONE attribute would evict each other and one half of the reading would vanish.
The control-watch firing is also the proof that the event happened at all, which is what makes the "dot did not fire" half mean
something.
"""
import io
import subprocess
import sys

print(subprocess.run([sys.executable, "tools/ship_debuff_class.py"], capture_output=True, text=True).stdout)

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
 * <p>ONE landed control, TWO watchers: a rule that asked for 「控制类」 must move ATTACK, and one that asked for 「持续伤害类」
 * must leave DEFENCE alone. The first is also the proof that the event happened at all, which is what gives the second meaning.
 *
 * <p>⚠ The readings are PANELS, not resources: a `ResourceSpec`-declared probe id was what made the earlier version of this test
 * report "nothing fired" when in fact both the landing and the event were fine.
 */
public class DebuffClassConditionTest {
    private static final int OWNER = 1002;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;

    /** The control rule fires; the dot rule does not. */
    @Test
    public void theFilterTellsTheTwoFamiliesApart() {
        double[] panels = run();
        double attackGain = panels[0];
        double defenceGain = panels[1];
        System.out.println("[debuff-class] control-watch ATTACK gain = " + attackGain
                + " ; dot-watch DEFENCE gain = " + defenceGain);

        Assertions.assertTrue(attackGain > EPS,
                "「控制类」-- the rule that asked for it fired, so the event really happened");
        Assertions.assertEquals(0.0, defenceGain, EPS,
                "「持续伤害类」-- the same event does NOT fire the rule that asked for the other family");
    }

    /** { the control-watch's ATTACK gain, the dot-watch's DEFENCE gain } after ONE control lands. */
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
        double attackBase = owner.getAttribute(AttributeType.ATTACK).baseValue();
        double defenceBase = owner.getAttribute(AttributeType.DEFENCE).baseValue();
        battle.startBattle();
        battle.processRequests();
        return new double[] {
                owner.getAttribute(AttributeType.ATTACK).get() - attackBase,
                owner.getAttribute(AttributeType.DEFENCE).get() - defenceBase};
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
print("ok   the judge reads panels instead of resources")
