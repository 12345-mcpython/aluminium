package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1408：「卡厄斯兰那**免疫控制类负面状态**」 (2026-10-02).
 *
 * <p>⭐ SAME SCENE, ONE VARIABLE: a spare unit applies 【冻结】 (a control, from the document's own glossary) to her; the only
 * difference between the two runs is whether she is transformed. The applier carries a hand-built table so the judge can cast a
 * control on demand -- and that unit's own content is irrelevant here, because only HER state is asserted.
 */
public class ControlImmunityTest {
    private static final int OWNER = 1408;
    private static final int APPLIER = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";
    private static final String FREEZE = "冻结";

    /** ⭐ Transformed: the control does not land. */
    @Test
    public void theTransformedFormShrugsOffControl() {
        Assertions.assertFalse(controlledAfter(true), "「卡厄斯兰那免疫控制类负面状态」");
    }

    /** ⚠ Untransformed: the same control lands, so the immunity really is the transformation's. */
    @Test
    public void withoutTheTransformationTheControlLands() {
        Assertions.assertTrue(controlledAfter(false), "「变身期间」-- outside it she is controlable");
    }

    // ==================================================================

    private static boolean controlledAfter(boolean transform) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character applier = CharacterFactory.create(APPLIER, 80);
        applier.setTriggerTable(new TriggerTable(APPLIER, List.of(
                TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), control()))));

        Battle battle = new Battle(List.of(owner, applier),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        if (transform) {
            Skill ult = owner.getSkills().get(SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: she has an ultimate");
            SkillExecutor.execute(battle, ult, owner, List.of(owner));
            battle.processRequests();
            Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        } else {
            Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "precondition: not transformed");
        }
        Assertions.assertFalse(owner.getBuffManager().hasState(FREEZE),
                "precondition: nothing has controlled her yet");

        // ⭐ NOW the control is aimed at her -- AFTER the transformation, which is the whole point. Measured: a control that lands at
        // BATTLE_START is simply there when the transformation begins, and immunity cannot retroactively remove it.
        Skill theirs = applier.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(theirs, "precondition: the applier has a skill");
        SkillExecutor.execute(battle, theirs, applier, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return owner.getBuffManager().hasState(FREEZE);
    }

    /** APPLY_CONTROL of 【冻结】 on the rule owner's own target -- the spare aims it at 1408 through the trigger's target. */
    private static EffectSpec control() {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "APPLY_CONTROL");
        TriggerSpecs.set(e, "control", FREEZE);
        TriggerSpecs.set(e, "turns", 3);
        // ⚠ `other_allies`, NOT `all_enemies`: from the applier's side the "enemies" are the monster, and the point of the judge is to
        // aim the control AT HER. Measured: with `all_enemies` the control went to the monster and the untransformed run never saw it.
        TriggerSpecs.set(e, "target", "other_allies");
        return e;
    }
}
