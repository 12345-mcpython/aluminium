package com.laosun.aluminium.test.content.characters.hysilens1410;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1410 Hysilens / 海瑟音: the Skill's damage-taken rule, her Eidolons 1 (the party DoT multiplier) and 4 (the zone's
 * resistance cut).
 *
 * <p>From her own file: the Skill's +20% damage taken, measured through actual damage.
 *
 * <p>Damage-taken is only observable as damage, so the case compares the loss a fixed hit causes BEFORE and AFTER the Skill: a hand-built 40% rule would double it, so the
 * content's 20% must land at 1.5x. That ratio is what makes the claim exact rather than "some increase".
 *
 * <p><b>Why a criterion.</b> The two numbers land on two different sides of the damage pipeline, and both have a
 * same-looking neighbour that would be wrong: the DoT multiplier is {@code DOT_DAMAGE_BOOST} on the PARTY (an additive
 * zone, so 0.16 is 「116% of the original」), while the resistance cut is {@code RESISTANCE_REDUCTION} on the ENEMIES -
 * positive on that attribute means "resists less", and it is not the attacker-side penetration.
 *
 * <p>The Eidolon 4 modifier must also share the zone's clock: it is granted on the same Ultimate, with the same
 * three-turn / own-turn ticks the zone's two existing modifiers use.
 */
public class Hysilens1410Test {
    private static final int HYSILENS = 1410;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * Note: The Skill raises the damage taken by 20%: a fixed hit costs 1.5x what it cost before (a 40% rule would give 2.0x).
     */
    @Test
    public void theSkillRaisesTheDamageTheEnemiesTake() {
        double before = fixedHitLoss(false);
        double after = fixedHitLoss(true);
        double doubled = referenceHitLoss();

        Assertions.assertTrue(before > 0, "the fixture must deal damage");
        // Measured: a "20% increased damage taken" makes the instance cost damage x 1.2 - the natural reading, and NOT the 1.5 I first assumed.
        Assertions.assertEquals(1.2, after / before, 0.02,
                "「受到的伤害提高20%」 (DMG taken is raised by 20%): before " + before + ", after " + after);
        // And the claim is the LINEAR share, with the 1.4 coming from a hand-built 40% rule in the same pipeline.
        Assertions.assertEquals(0.5, (after / before - 1.0) / (doubled / before - 1.0), 0.05,
                "20% against a 40% reference must be half the increase: " + (after / before - 1.0) + " vs " + (doubled / before - 1.0));
    }

    /**
     * Eidolon 1: 「我方目标造成的持续伤害为原伤害的 116%」 - 0.16 into the DoT boost zone, for the whole party.
     */
    @Test
    public void theFirstEidolonRaisesThePartysDotDamage() {
        Run e0 = new Run(0);
        Run e1 = new Run(1);
        double plain = e0.allyDotBoost();
        double raised = e1.allyDotBoost();
        System.out.println("[1410-E1] ally DoT boost " + plain + " -> " + raised);
        Assertions.assertEquals(0.0, plain, 1e-9, "E0 states no such multiplier");
        Assertions.assertEquals(0.16, raised - plain, 1e-9,
                "「116% of the original」 is +16% into the additive DoT zone");
    }

    /**
     * Eidolon 4: 「结界持续期间，使敌方全体全属性抗性降低 20%」.
     */
    @Test
    public void theFourthEidolonCutsEveryEnemysResistance() {
        Run e3 = new Run(3);
        Run e4 = new Run(4);
        e3.battle.castImmediate(e3.hysilens.getSkills().get(SkillType.ULTRA), e3.hysilens,
                List.of(e3.battle.enemies().getFirst()));
        e4.battle.castImmediate(e4.hysilens.getSkills().get(SkillType.ULTRA), e4.hysilens,
                List.of(e4.battle.enemies().getFirst()));
        e3.battle.processRequests();
        e4.battle.processRequests();

        double plain = e3.battle.enemies().getFirst().getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        double cut = e4.battle.enemies().getFirst().getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        System.out.println("[1410-E4] enemy resistance reduction " + plain + " -> " + cut);
        Assertions.assertEquals(0.2, cut - plain, 1e-9,
                "「全属性抗性降低 20%」 - positive on RESISTANCE_REDUCTION lowers the target's resistance");
        // The control: the attacker-side penetration is untouched (the two attributes are different readers).
        Assertions.assertEquals(
                e3.hysilens.getAttribute(AttributeType.DAMAGE_PENETRATION).get(),
                e4.hysilens.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), 1e-9,
                "this is the victim's resistance, not the attacker's penetration");
    }

    // ==================================================================

    /**
     * Hits the enemy with a fixed instance, optionally after Hysilens' Skill, and returns the loss.
     */
    private static double fixedHitLoss(boolean afterSkill) {
        Character hysilens = CharacterFactory.create(HYSILENS, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
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

    /**
     * The same hit with a hand-built 40% damage-taken rule, for the scale of comparison.
     */
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
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(hysilens, ally), List.of(enemy), fixed());
        battle.startBattle();
        battle.castImmediate(hysilens.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), hysilens, List.of(enemy));
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new Damage(ally, enemy, com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    private static final class Run {
        private final Character hysilens;
        private final Character ally;
        private final Battle battle;

        Run(int eidolon) {
            hysilens = CharacterFactory.create(HYSILENS, LEVEL);
            hysilens.setEidolonRank(eidolon);
            ally = CharacterFactory.create(ALLY, LEVEL);
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(List.of(hysilens, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        double allyDotBoost() {
            return ally.getAttribute(AttributeType.DOT_DAMAGE_BOOST).get();
        }
    }
}
