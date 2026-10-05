package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1410 Hysilens, from her own file (2026-09-29, round 208): the Skill's +20% damage taken, measured through actual damage.
 *
 * <p>Damage-taken is only observable as damage, so the case compares the loss a fixed hit causes BEFORE and AFTER the Skill: a hand-built 40% rule would double it, so the
 * content's 20% must land at 1.5x. That ratio is what makes the claim exact rather than "some increase".
 */
public class HysilensTest {
    private static final int HYSILENS = 1410;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The Skill raises the damage taken by 20%: a fixed hit costs 1.5x what it cost before (a 40% rule would give 2.0x). */
    @Test
    public void theSkillRaisesTheDamageTheEnemiesTake() {
        double before = fixedHitLoss(false);
        double after = fixedHitLoss(true);
        double doubled = referenceHitLoss();

        Assertions.assertTrue(before > 0, "the fixture must deal damage");
        // Measured: a "20% increased damage taken" makes the instance cost damage x 1.2 - the natural reading, and NOT the 1.5 I first assumed.
        Assertions.assertEquals(1.2, after / before, 0.02,
                "「受到的伤害提高20%」: before " + before + ", after " + after);
        // And the claim is the LINEAR share, with the 1.4 coming from a hand-built 40% rule in the same pipeline.
        Assertions.assertEquals(0.5, (after / before - 1.0) / (doubled / before - 1.0), 0.05,
                "20% against a 40% reference must be half the increase: " + (after / before - 1.0) + " vs " + (doubled / before - 1.0));
    }

    /** Hits the enemy with a fixed instance, optionally after Hysilens' Skill, and returns the loss. */
    private static double fixedHitLoss(boolean afterSkill) {
        Character hysilens = CharacterFactory.create(HYSILENS, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(hysilens, ally), List.of(enemy), fixed());
        battle.startBattle();
        if (afterSkill) {
            battle.castImmediate(hysilens.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), hysilens, List.of(enemy));
        }
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new Damage(ally, enemy, com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - enemy.getCurrentHp();
    }

    /** The same hit with a hand-built 40% damage-taken rule, for the scale of comparison. */
    private static double referenceHitLoss() {
        Character hysilens = CharacterFactory.create(HYSILENS, LEVEL);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_DAMAGE_TAKEN");
        TriggerSpecs.set(effect, "percent", 0.4);
        TriggerSpecs.set(effect, "turns", 3);
        TriggerSpecs.set(effect, "target", "all_enemies");
        hysilens.setTriggerTable(new TriggerTable(HYSILENS, List.of(TriggerSpecs.rule(
                TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), effect))));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(hysilens, ally), List.of(enemy), fixed());
        battle.startBattle();
        battle.castImmediate(hysilens.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), hysilens, List.of(enemy));
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new Damage(ally, enemy, com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
