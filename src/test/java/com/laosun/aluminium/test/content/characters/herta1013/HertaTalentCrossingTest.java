package com.laosun.aluminium.test.content.characters.herta1013;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1013 黑塔's Talent: 「当任意我方目标的攻击使敌方目标的当前生命值百分比**降到**小于等于 50% 时，黑塔发动追加攻击，对敌方全体造成…
 * 40% 攻击力冰伤」 - and the two Eidolons that hang off it.
 *
 * <p><b>Why a criterion.</b> The trigger is a CROSSING, and the engine's only spelling for it is the pair
 * {@code target_hp_percent_before > 0.5} + {@code target_hp_percent <= 0.5}. The wrong reading ("is below half") fires on
 * every later hit; this file separates the two cases on a second, untouched enemy, because the follow-up is an AoE: the
 * victim that crosses cannot show whether the attack or the follow-up did the damage, but a bystander at full HP can.
 *
 * <p>Eidolon 2 (「天赋每触发 1 次，暴击率 +3%，最多 5 层」) and Eidolon 4 (「触发天赋时造成的伤害提高 10%」) are measured on
 * the same trigger, so they also fail if the crossing fires too often or not at all.
 */
public class HertaTalentCrossingTest {
    private static final int HERTA = 1013;
    private static final int LEVEL = 80;

    /**
     * The crossing pays the follow-up (the bystander is hit); a hit on an enemy already below half does not.
     */
    @Test
    public void onlyTheCrossingPaysTheFollowup() {
        Fixture crossing = new Fixture(0, 0.60);
        crossing.crossHalf();
        Assertions.assertTrue(crossing.bystanderLoss() > 0,
                "the follow-up is an AoE: an enemy that did NOT cross must still be hit");

        Fixture alreadyBelow = new Fixture(0, 0.40);
        alreadyBelow.hit(0.10);
        Assertions.assertEquals(0.0, alreadyBelow.bystanderLoss(), 1e-9,
                "「降到」is the crossing; a target already below half does not pay it again");
    }

    /**
     * Eidolon 2: one crit-rate stack per trigger, capped at five - six crossings read five.
     */
    @Test
    public void theSecondEidolonStacksOneCritStepPerTrigger() {
        Fixture e2 = new Fixture(2, 0.60);
        double before = e2.herta.getAttribute(AttributeType.CRIT_CHANCE).get();
        e2.crossHalf();
        Assertions.assertEquals(0.03, e2.herta.getAttribute(AttributeType.CRIT_CHANCE).get() - before, 1e-9,
                "「天赋每触发 1 次，自身暴击率提高 3%」");

        for (int i = 0; i < 5; i++) {
            e2.healAndCrossAgain();
        }
        Assertions.assertEquals(0.15, e2.herta.getAttribute(AttributeType.CRIT_CHANCE).get() - before, 1e-9,
                "「最多叠加 5 层」 - six triggers (one above) must read 5 x 3%");

        Fixture e1 = new Fixture(1, 0.60);
        double control = e1.herta.getAttribute(AttributeType.CRIT_CHANCE).get();
        e1.crossHalf();
        Assertions.assertEquals(control, e1.herta.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9,
                "at E1 the stack does not exist");
    }

    /**
     * Eidolon 4: the follow-up itself hits harder (an additive +10% into the instance's own boost zone).
     */
    @Test
    public void theFourthEidolonRaisesTheFollowupDamage() {
        Fixture e3 = new Fixture(3, 0.60);
        e3.crossHalf();
        Fixture e4 = new Fixture(4, 0.60);
        e4.crossHalf();
        double plain = e3.bystanderLoss();
        double raised = e4.bystanderLoss();
        double ratio = raised / plain;
        System.out.println("[1013-E4] followup E3=" + plain + " E4=" + raised + " ratio=" + ratio);
        Assertions.assertTrue(plain > 0, "precondition: E3 already pays the follow-up");
        Assertions.assertTrue(ratio > 1.05 && ratio <= 1.10,
                "「触发天赋时造成的伤害提高 10%」is additive into the boost zone, so the ratio is in (1.05, 1.10]: " + ratio);
    }

    // ==================================================================

    /** Herta at one Eidolon rank, a victim at a stated HP fraction, and a bystander at full HP. */
    private static final class Fixture {
        private final Character herta;
        private final Enemy victim;
        private final Enemy bystander;
        private final Battle battle;
        /** The base of one hit: large enough to cross from 60% to below half, small enough not to kill (her boost zone is ~1.22). */
        private static final double HIT = 0.3;

        Fixture(int eidolon, double victimFraction) {
            herta = CharacterFactory.create(HERTA, LEVEL);
            herta.setEidolonRank(eidolon);
            victim = BattleFixture.dummy();
            bystander = BattleFixture.dummy();
            battle = new Battle(List.of(herta), List.of(victim, bystander), fixed());
            battle.startBattle();
            // Raw HP, no events: lowering the fixture must not run the Talent before the measurement starts.
            victim.takeDamage(victim.getMaxHp() * (1 - victimFraction));
        }

        /** One hit on the victim, crossing half if the fixture starts above it. */
        void crossHalf() {
            hit(HIT);
        }

        /** Heal the victim back to full, take it down to 60% again, then cross the line - one more Talent trigger. */
        void healAndCrossAgain() {
            victim.heal(victim.getMaxHp());
            victim.takeDamage(victim.getMaxHp() * 0.4);
            hit(HIT);
        }

        void hit(double base) {
            battle.applyDamage(victim, new Damage(herta, victim, DamageElement.ICE, DamageType.NORMAL,
                    victim.getMaxHp() * base));
        }

        /** What the bystander lost - only the follow-up can have reached it. */
        double bystanderLoss() {
            return bystander.getMaxHp() - bystander.getCurrentHp();
        }
    }

    private static Random fixed() {
        // 1.0 = no crit: the measurements must not depend on a roll.
        return BattleFixture.alwaysRoll(1.0);
    }
}
