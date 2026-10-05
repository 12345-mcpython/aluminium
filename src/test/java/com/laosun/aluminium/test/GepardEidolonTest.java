package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Gepard (杰帕德)'s eidolon 1, verified through the engine's own reading (2026-09-28, round 130).
 *
 * <p><b>How the source settled it.</b> `TriggerInterpreter.modifyRule` dispatches on which field the effect carries: `amount` is a
 * per-turn count, `effect_turns` is a duration, `effect_max_stacks` is a stack cap, `effect_percent` is the rule's effect
 * percentage, and - the branch that matters here - <b>a plain `percent` is the base-chance amendment</b>
 * (`owner.addRuleBaseChanceBonus(target, percent)`). The control's roll reads it back as
 * `baseChance += ctx.owner().ruleBaseChanceBonus(ctx.ruleId())`, so the amendment is keyed by the id of the rule that applies
 * the control.
 *
 * <p>That gives a deterministic assertion for "冻结的基础概率提高35%" that does not touch the dice at all: ask the combatant what
 * it has filed. The freeze itself stays unverified - even at E1 it did not land under the fixture, which is now narrowed to
 * either the roll or the state's name/resistance rather than to this op.
 */
public class GepardEidolonTest {
    private static final int GEPARD = 1104;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: E1 files a +0.35 base-chance amendment under the id of the rule that applies the freeze. */
    @Test
    public void eidolonOneFilesTheBaseChanceAmendment() {
        Character atE0 = gepardAt(0);
        Character atE1 = gepardAt(1);

        double none = atE0.ruleBaseChanceBonus("skill_daunting_smite");
        double raised = atE1.ruleBaseChanceBonus("skill_daunting_smite");

        Assertions.assertEquals(0.0, none, 1e-9,
                "at E0 nothing is filed for that rule");
        Assertions.assertEquals(0.35, raised, 1e-9,
                "「施放战技时，使受到攻击的敌方目标陷入冻结状态的基础概率提高35%」 — filed as a BASE CHANCE amendment, "
                        + "which is what a plain `percent` on MODIFY_RULE means");
    }

    private static Character gepardAt(int eidolon) {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, eidolon);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(gepard), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        battle.startBattle();
        return gepard;
    }
}
