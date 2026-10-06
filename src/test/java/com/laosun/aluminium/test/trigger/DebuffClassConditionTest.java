package com.laosun.aluminium.test.trigger;


import com.laosun.aluminium.test.engine.ControlImmunityTest;
import com.laosun.aluminium.test.support.TriggerSpecs;
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
 * "敌方对我方施加了控制类负面状态" (an enemy applied a control-class negative state to our side) can now be asked.
 *
 * <p>One landed control, two watchers; both readings are deltas across startBattle. The landing is copied from the green
 * `ControlImmunityTest` - including its measured warning that `all_enemies` aims at the MONSTER - so that a failure here is about the
 * filter and not about the scene.
 */
public class DebuffClassConditionTest {
    private static final int OWNER = 1002;
    private static final int MONSTER = 1002011;
    private static final String FREEZE = "冻结";
    private static final double EPS = 1e-9;

    /** The control rule fires; the dot rule does not. */
    @Test
    public void theFilterTellsTheTwoFamiliesApart() {
        double[] gains = run();
        System.out.println("[debuff-class] control=" + gains[0] + " dot=" + gains[1]);
        Assertions.assertTrue(gains[0] > EPS,
                "「控制类」 (the control class)-- the rule that asked for it fired, which also proves the event happened");
        Assertions.assertEquals(0.0, gains[1], EPS,
                "「持续伤害类」 (the DOT class)-- the same event does NOT fire the rule that asked for the other family");
    }

    /** { the control-watch's ATTACK gain, the dot-watch's DEFENCE gain }. */
    private static double[] run() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);

        EffectSpec land = new EffectSpec();
        TriggerSpecs.set(land, "op", "APPLY_CONTROL");
        TriggerSpecs.set(land, "control", FREEZE);
        TriggerSpecs.set(land, "turns", 3);
        // Note: Verbatim from the green judge: from the applier's side the "enemies" are the monster, and a judge about the rule owner
        // wants the control ON the owner's own camp.
        TriggerSpecs.set(land, "target", "other_allies");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), land),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of("debuff_class:control"), boost("ATTACK")),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of("debuff_class:dot"), boost("DEFENCE"))),
                List.of()));

        Character other = CharacterFactory.create(1003, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, other),
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
