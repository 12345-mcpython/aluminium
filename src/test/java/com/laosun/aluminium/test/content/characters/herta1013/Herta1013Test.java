package com.laosun.aluminium.test.content.characters.herta1013;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1013 Herta, her two files merged: the Skill (a per-target threshold bonus and a control-class resistance) and the
 * Talent (the HP-crossing follow-up, plus the two Eidolons that hang off it).
 *
 * <p>The skill case uses two enemies on ONE battlefield - one at full HP and one already below half - so the claim "对该目标" is measured
 * rather than assumed: the same cast must hurt the healthy one proportionally more.
 *
 * <p><b>Why a criterion for the Talent.</b> The trigger is a CROSSING, and the engine's only spelling for it is the pair
 * {@code target_hp_percent_before > 0.5} + {@code target_hp_percent <= 0.5}. The wrong reading ("is below half") fires on
 * every later hit; the crossing case is separated from it on a second, untouched enemy, because the follow-up is an AoE: the
 * victim that crosses cannot show whether the attack or the follow-up did the damage, but a bystander at full HP can.
 *
 * <p>Eidolon 2 (「天赋每触发 1 次，暴击率 +3%，最多 5 层」) and Eidolon 4 (「触发天赋时造成的伤害提高 10%」) are measured on
 * the same trigger, so they also fail if the crossing fires too often or not at all.
 */
public class Herta1013Test {
    private static final int HERTA = 1013;
    private static final int LEVEL = 80;

    // ==================================================================
    // The Skill: the control-class resistance and the per-target threshold bonus.
    // ==================================================================

    /**
     * Note: "抵抗控制类负面状态的概率提高35%": the engine keeps a per-class resistance, and it must be up.
     */
    @Test
    public void herPuppetTraceRaisesControlResistance() {
        ThresholdFixture f = new ThresholdFixture();
        Assertions.assertTrue(f.herta.getBuffManager().debuffResistOf(DebuffClass.CONTROL) > 0,
                "「抵抗控制类负面状态的概率提高35%」 (raises RES to Crowd Control debuffs by 35%)");
    }

    /**
     * Note: "若敌方目标当前生命值百分比大于等于50%": the bonus is per TARGET, so a low-HP enemy must not get it.
     */
    @Test
    public void theSkillBonusAppliesOnlyToHealthyTargets() {
        Character herta = CharacterFactory.create(HERTA, LEVEL);
        Enemy healthy = Enemy.fromAttributes("Healthy Dummy", 40000, 500, 100, 90);
        Enemy hurt = Enemy.fromAttributes("Hurt Dummy", 40000, 500, 100, 90);
        Battle battle = new Battle(java.util.List.of(herta), java.util.List.of(healthy, hurt), new java.util.Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        battle.startBattle();
        // Below the threshold, settled through the engine (its category is UNSPECIFIED, so her rule cannot apply to it).
        battle.applyDamage(hurt, new com.laosun.aluminium.damage.Damage(herta, hurt,
                com.laosun.aluminium.enums.DamageElement.ICE,
                com.laosun.aluminium.enums.DamageType.NORMAL, hurt.getMaxHp() * 0.6));
        double healthyBefore = healthy.getCurrentHp();
        double hurtBefore = hurt.getCurrentHp();

        battle.castImmediate(herta.getSkills().get(SkillType.SKILL), herta, java.util.List.of(healthy, hurt));

        double healthyLoss = healthyBefore - healthy.getCurrentHp();
        double hurtLoss = hurtBefore - hurt.getCurrentHp();
        // Same defence on both sides, so an equal instance would cost each the same ABSOLUTE HP, and the document's 20% has to show up
        // as at least a 10% larger loss. (Shares would differ by construction; a strict `>` is satisfied by float noise.)
        Assertions.assertTrue(healthyLoss > hurtLoss * 1.1,
                "「若敌方目标当前生命值百分比大于等于50%，则对该目标造成的伤害提高20%」 (if the enemy target's current HP percentage is at least 50%, DMG dealt to that target is raised by 20%) -- absolute losses: "
                        + healthyLoss + " (healthy) vs " + hurtLoss + " (hurt)");
    }

    /** Herta plus one healthy and one already-hurt dummy on ONE battlefield. */
    private static final class ThresholdFixture {
        private final Character herta = CharacterFactory.create(HERTA, LEVEL);
        private final Enemy healthy = Enemy.fromAttributes("Healthy Dummy", 40000, 500, 100, 90);
        private final Enemy hurt = Enemy.fromAttributes("Hurt Dummy", 40000, 500, 100, 90);
        private final Battle battle;

        private ThresholdFixture() {
            battle = new Battle(List.of(herta), List.of(healthy, hurt), new Random() {
                @Override
                public double nextDouble() {
                    return 0.0;
                }
            });
            battle.startBattle();
            // Put the second dummy below the threshold without touching the first. `setCurrentHp` does not exist, so the
            // engine settles a plain instance instead; its cast category is UNSPECIFIED, so her skill rule cannot apply to it.
            battle.applyDamage(hurt, new com.laosun.aluminium.damage.Damage(herta, hurt,
                    com.laosun.aluminium.enums.DamageElement.ICE,
                    com.laosun.aluminium.enums.DamageType.NORMAL, hurt.getMaxHp() * 0.6));
        }
    }

    // ==================================================================
    // The Talent: the HP crossing, its follow-up, and Eidolons 2 / 4.
    // ==================================================================

    /**
     * The crossing pays the follow-up (the bystander is hit); a hit on an enemy already below half does not.
     */
    @Test
    public void onlyTheCrossingPaysTheFollowup() {
        CrossingFixture crossing = new CrossingFixture(0, 0.60);
        crossing.crossHalf();
        Assertions.assertTrue(crossing.bystanderLoss() > 0,
                "the follow-up is an AoE: an enemy that did NOT cross must still be hit");

        CrossingFixture alreadyBelow = new CrossingFixture(0, 0.40);
        alreadyBelow.hit(0.10);
        Assertions.assertEquals(0.0, alreadyBelow.bystanderLoss(), 1e-9,
                "「降到」is the crossing; a target already below half does not pay it again");
    }

    /**
     * Eidolon 2: one crit-rate stack per trigger, capped at five - six crossings read five.
     */
    @Test
    public void theSecondEidolonStacksOneCritStepPerTrigger() {
        CrossingFixture e2 = new CrossingFixture(2, 0.60);
        double before = e2.herta.getAttribute(AttributeType.CRIT_CHANCE).get();
        e2.crossHalf();
        Assertions.assertEquals(0.03, e2.herta.getAttribute(AttributeType.CRIT_CHANCE).get() - before, 1e-9,
                "「天赋每触发 1 次，自身暴击率提高 3%」");

        for (int i = 0; i < 5; i++) {
            e2.healAndCrossAgain();
        }
        Assertions.assertEquals(0.15, e2.herta.getAttribute(AttributeType.CRIT_CHANCE).get() - before, 1e-9,
                "「最多叠加 5 层」 - six triggers (one above) must read 5 x 3%");

        CrossingFixture e1 = new CrossingFixture(1, 0.60);
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
        CrossingFixture e3 = new CrossingFixture(3, 0.60);
        e3.crossHalf();
        CrossingFixture e4 = new CrossingFixture(4, 0.60);
        e4.crossHalf();
        double plain = e3.bystanderLoss();
        double raised = e4.bystanderLoss();
        double ratio = raised / plain;
        System.out.println("[1013-E4] followup E3=" + plain + " E4=" + raised + " ratio=" + ratio);
        Assertions.assertTrue(plain > 0, "precondition: E3 already pays the follow-up");
        Assertions.assertTrue(ratio > 1.05 && ratio <= 1.10,
                "「触发天赋时造成的伤害提高 10%」is additive into the boost zone, so the ratio is in (1.05, 1.10]: " + ratio);
    }

    /**
     * 1013 黑塔: the Skill hits harder on a target above half HP («对生命值百分比大于等于50%的目标…伤害提高 20%»).
     *
     * <p>⚠ The measured ratio is NOT 1.2: {@code BOOST_DAMAGE} adds into a zone that already carries something, so the ratio is
     * {@code (1.2 + B) / (1 + B)} — measured 1.163, which fits B ≈ 0.23. The judge therefore pins a BAND (well above 1.0, below
     * a plain 1.2): that still fails loudly if the clause ever stops applying, which is what an earlier note wrongly claimed it
     * already had.
     */
    @Test
    public void theSkillHitsHarderAboveHalfHp() {
        double high = damageAt(1.0);
        double low = damageAt(0.3);
        System.out.println("[1013] skill damage: full HP " + high + " ; low HP " + low + " ; ratio " + (high / low));
        Assertions.assertTrue(high > low, "「对高血量目标伤害提高」 must land");
        double ratio = high / low;
        Assertions.assertTrue(ratio > 1.10 && ratio <= 1.2,
                "an additive +20% into a zone that already carries something lands between 1.10 and 1.20, got " + ratio);
    }

    private static double damageAt(double fraction) {
        Character her = CharacterFactory.create(HERTA, LEVEL);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(her)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        for (int step = 0; step < 200 && enemy.getCurrentHp() > enemy.getMaxHp() * fraction; step++) {
            battle.applyDamage(enemy, new Damage(her, enemy, DamageElement.ICE,
                    DamageType.NORMAL, enemy.getMaxHp() * 0.01));
            if (enemy.isDeath()) {
                break;
            }
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(her.getSkills().get(SkillType.SKILL), her, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    /** Herta at one Eidolon rank, a victim at a stated HP fraction, and a bystander at full HP. */
    private static final class CrossingFixture {
        private final Character herta;
        private final Enemy victim;
        private final Enemy bystander;
        private final Battle battle;
        /** The base of one hit: large enough to cross from 60% to below half, small enough not to kill (her boost zone is ~1.22). */
        private static final double HIT = 0.3;

        CrossingFixture(int eidolon, double victimFraction) {
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
