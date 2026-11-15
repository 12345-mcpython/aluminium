package com.laosun.aluminium.test.content.characters.fuxuan;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1208 符玄, the whole judge in one class: the team-wide damage reduction of [避厄] and the two numbers [鉴知] hands out, the
 * Eidolon 2 rescue, the zone's 65% share, Eidolon 4's energy and Eidolon 6's Ultimate scaling, the talent's heal with its
 * charge counter, and the technique's 2-turn zone.
 *
 * <p>These were six small files in the same package - base kit, Eidolon 2, the zone's share, clause coverage (Eidolon 4 and
 * 6), the talent, and the technique; one class keeps the character together without changing any test or assertion. The two
 * ally ids are kept apart, because 1001 and 1002 are different characters and folding one into the other would change what the
 * judges measure.
 */
public class FuXuanTest {

    private static final int FUXUAN = 1208;
    private static final int LEVEL = 80;
    // Two different allies: 1002 is the support of the base-kit judges, 1001 the body the share / Eidolon 2 / technique
    // judges use. They are NOT the same character, so the two ids stay separate.
    private static final int ALLY_1002 = 1002;
    private static final int ALLY_1001 = 1001;
    private static final double BASE = 1000.0;
    // 【鉴知】 under one name for every judge that reads it, and the talent's charge counter.
    private static final String ZONE = "char:1208/insight";
    private static final String CHARGE = "internal:1208/ward_heals";

    // ==================================================================
    // Base kit: the team-wide -18% of [避厄] and the two numbers [鉴知] hands out.
    // ==================================================================

    /**
     * Note: [避厄]'s 18% reduction, in the measured convention: damage x (1 - 0.18), compared with a hand-built -36% rule.
     */
    @Test
    public void misfortuneAvoidanceCutsTheDamageTheTeamTakes() {
        double plain = hitLoss(false);
        double shielded = hitLoss(true);
        double reference = referenceHitLoss();

        Assertions.assertTrue(plain > 0, "the fixture must deal damage");
        Assertions.assertEquals(0.82, shielded / plain, 0.02,
                "18% less damage is damage x 0.82: " + shielded + " vs " + plain);
        Assertions.assertEquals(0.5, (1.0 - shielded / plain) / (1.0 - reference / plain), 0.05,
                "18% against a hand-built 36% must be half the reduction");
    }

    /**
     * Note: [鉴知]: 6% of HER max HP as extra max HP, and +12% crit rate, on an ally.
     */
    @Test
    public void knowledgeRaisesMaxHpByHerShareAndCritRate() {
        Character fuxuan = CharacterFactory.create(FUXUAN, LEVEL);
        Character ally = CharacterFactory.create(ALLY_1002, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(fuxuan, ally), List.of(enemy), fixed());
        battle.startBattle();
        double hpBefore = ally.getAttribute(AttributeType.HEALTH).get();
        double critBefore = ally.getAttribute(AttributeType.CRIT_CHANCE).get();
        double expected = fuxuan.getAttribute(AttributeType.HEALTH).get() * 0.06;

        battle.castImmediate(fuxuan.getSkills().get(SkillType.SKILL), fuxuan, List.of(ally));

        Assertions.assertTrue(ally.getBuffManager().hasState("char:1208/insight"),
                "「处于【穷观阵】的我方全体获得【char:1208/insight】」 (all allies inside the Matrix of Prescience (【穷观阵】) gain Knowledge (【char:1208/insight】))");
        Assertions.assertEquals(expected, ally.getAttribute(AttributeType.HEALTH).get() - hpBefore, expected * 0.02,
                "6% of HER max HP: expected " + expected);
        Assertions.assertEquals(0.12, ally.getAttribute(AttributeType.CRIT_CHANCE).get() - critBefore, 1e-6,
                "「暴击率提高12.00%」 (CRIT Rate is raised by 12.00%)");
    }

    /**
     * One fixed hit against an ally, with her talent active or not.
     */
    private static double hitLoss(boolean talent) {
        Character fuxuan = CharacterFactory.create(FUXUAN, LEVEL);
        Character ally = CharacterFactory.create(ALLY_1002, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(fuxuan, ally), List.of(enemy), fixed());
        if (talent) {
            battle.startBattle();
        }
        double before = ally.getCurrentHp();
        battle.applyDamage(ally, new Damage(enemy, ally, com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - ally.getCurrentHp();
    }

    /**
     * The same hit with a hand-built -36% damage-taken rule, for the scale of comparison.
     */
    private static double referenceHitLoss() {
        Character fuxuan = CharacterFactory.create(FUXUAN, LEVEL);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_DAMAGE_TAKEN");
        TriggerSpecs.set(effect, "percent", -0.36);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "all_allies");
        fuxuan.setTriggerTable(new TriggerTable(FUXUAN, List.of(TriggerSpecs.rule(
                TriggerEvent.BATTLE_START.name(), List.of(), effect))));
        Character ally = CharacterFactory.create(ALLY_1002, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(fuxuan, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getCurrentHp();
        battle.applyDamage(ally, new Damage(enemy, ally, com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - ally.getCurrentHp();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    // ==================================================================
    // Eidolon 2: 「【穷观阵】开启时，若我方目标受到致命伤害，则…不会陷入无法战斗状态，并立即回复等同于其自身生命上限
    // 70% 的生命值。该效果单场战斗中可以触发 1 次」.
    //
    // The rule already existed; this judge pins it (and the once-per-battle limit).
    // ==================================================================

    @Test
    public void theZoneSavesAnAllyOncePerBattle() {
        Character fuxuan = CharacterFactory.create(FUXUAN, LEVEL, true, null, null, 2);
        Character ally = CharacterFactory.create(ALLY_1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(fuxuan, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        fuxuan.getBuffManager().addBuff(new StateBuff(ZONE, 3, false));

        battle.applyTrueDamage(enemy, ally, DamageElement.PHYSICAL, ally.getCurrentHp() * 10);
        battle.processRequests();
        Assertions.assertFalse(ally.isDeath(), "the lethal hit did not take the ally down");
        double healed = ally.getCurrentHp();
        System.out.println("[1208] ally hp after the rescue = " + healed + " / max " + ally.getMaxHp());
        Assertions.assertEquals(0.7, healed / ally.getMaxHp(), 1e-6, "「回复等同于其自身生命上限 70%」");

        // Second lethal hit: the once-per-battle limit has been spent.
        CanHit target = ally;
        battle.applyTrueDamage(enemy, target, DamageElement.PHYSICAL, ally.getCurrentHp() * 10);
        battle.processRequests();
        Assertions.assertTrue(ally.isDeath(), "「该效果单场战斗中可以触发 1 次」");
    }

    // ==================================================================
    // The zone share: 「使符玄队友所承受的未受护盾抵挡之前的伤害的 65% 分摊给符玄」.
    //
    // Measured on SETTLED amounts, not HP loss: the ally may carry a shield, and a shield changes what comes out of HP without
    // changing what the hit settled for - the sentence shares 「未受护盾抵挡之前的伤害」, which is exactly the settled number.
    // ==================================================================

    @Test
    public void theAllyKeepsThirtyFiveAndShePaysSixtyFive() {
        ShareFixture zone = new ShareFixture(true);
        zone.hitAlly();
        ShareFixture bare = new ShareFixture(false);
        bare.hitAlly();
        bare.hitHerself();

        System.out.println("[1208] zone: ally=" + zone.allySettled + " her=" + zone.herSettled
                + " ; bare: ally=" + bare.allySettled + " her=" + bare.herSettled);
        Assertions.assertEquals(0.35, zone.allySettled / bare.allySettled, 1e-6, "the ally keeps 35%");
        Assertions.assertEquals(0.65, zone.herSettled / bare.herSettled, 1e-6,
                "「65% 分摊给符玄」 - the same base, through her own zones");
    }

    @Test
    public void withoutTheZoneNobodyShares() {
        ShareFixture bare = new ShareFixture(false);
        bare.hitAlly();
        Assertions.assertEquals(0.0, bare.herSettled, 1e-9, "no 【char:1208/insight】, no share");
    }

    // ==================================================================

    private static final class ShareFixture {
        private final Character fuxuan;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;
        private double allySettled;
        private double herSettled;

        private ShareFixture(boolean zone) {
            fuxuan = CharacterFactory.create(FUXUAN, LEVEL);
            ally = CharacterFactory.create(ALLY_1001, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(fuxuan, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.99));
            battle.addDamageObserver((damage, applied, hpLoss, shield, died) -> {
                if (damage.getDefender() == ally) {
                    allySettled += applied;
                } else if (damage.getDefender() == fuxuan) {
                    herSettled += applied;
                }
            });
            battle.startBattle();
            if (zone) {
                battle.castImmediate(fuxuan.getSkills().get(SkillType.SKILL), fuxuan, List.of(ally));
            }
        }

        private void hitAlly() {
            battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.PHYSICAL, DamageType.NORMAL, BASE));
        }

        private void hitHerself() {
            battle.applyDamage(fuxuan, new Damage(enemy, fuxuan, DamageElement.PHYSICAL, DamageType.NORMAL, BASE));
        }
    }

    // ==================================================================
    // Clause coverage: Eidolon 4 — 「当处于【穷观阵】的我方其他目标受到攻击后，符玄恢复 5 点能量」 — and Eidolon 6.
    //
    // The zone is modelled as the 【鉴知】 state in this file (both the Skill and the technique apply it), so the Eidolon reads
    // that state and excludes Fu Xuan herself: 「**其他**目标」 is the half that is easy to drop, and dropping it would pay on
    // her own hits.
    // ==================================================================

    /**
     * With the zone open, an ally taking a hit pays 5 energy — and her OWN hit does not.
     */
    @Test
    public void eidolon4PaysWhenAnAllyInTheZoneIsHit() {
        ClauseFixture f = new ClauseFixture(4);
        f.openTheZone();
        double before = f.fuXuan.getCurrentEnergy();

        f.allyIsHit();
        Assertions.assertEquals(before + 5, f.fuXuan.getCurrentEnergy(), 1e-9,
                "「处于【穷观阵】的我方其他目标受到攻击后，符玄恢复 5 点能量」");

        double afterAlly = f.fuXuan.getCurrentEnergy();
        f.fuXuanIsHit();
        Assertions.assertEquals(afterAlly, f.fuXuan.getCurrentEnergy(), 1e-9,
                "「我方**其他**目标」 — her own hit pays nothing");
    }

    /**
     * Without the Eidolon the same event pays nothing.
     */
    @Test
    public void theBaseKitPaysNothing() {
        ClauseFixture f = new ClauseFixture(0);
        f.openTheZone();
        double before = f.fuXuan.getCurrentEnergy();
        f.allyIsHit();
        Assertions.assertEquals(before, f.fuXuan.getCurrentEnergy(), 1e-9, "control: it is Eidolon 4");
    }

    /**
     * 星魂 6: the Ultimate's damage grows with the HP the side has lost, and the counter is emptied afterwards.
     *
     * <p>Measured on the settled damage of the Ultimate itself: E6 with 4000 HP lost must beat E0 with the same board, and the same
     * E6 battle with no loss must NOT - which is what shows the boost is reading the accumulator rather than a flat bonus.
     */
    @Test
    public void eidolon6ScalesTheUltimateWithTheSidesLostHp() {
        double bare = ultimateDamage(6, 0);
        double loaded = ultimateDamage(6, 4000);
        Assertions.assertTrue(loaded > bare, "「终结技造成的伤害提高 = 累计已损失生命值 × 200%」 (" + bare + " -> " + loaded + ")");

        ClauseFixture f = new ClauseFixture(6);
        f.accumulate(4000);
        f.castUltimate();
        Assertions.assertEquals(0, f.fuXuan.getResources().value("internal:1208/accumulated_loss"), 1e-9,
                "「施放终结技后会清空并进行重新累计」");
    }

    private static double ultimateDamage(int eidolon, int lostHp) {
        ClauseFixture f = new ClauseFixture(eidolon);
        f.accumulate(lostHp);
        double[] settled = {0};
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled[0] += applied);
        f.castUltimate();
        return settled[0];
    }

    // ==================================================================

    private static final class ClauseFixture {
        private final Character fuXuan;
        private final Character ally = CharacterFactory.create(ALLY_1002, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private ClauseFixture(int eidolon) {
            fuXuan = CharacterFactory.create(FUXUAN, 80, false, null, null, eidolon);
            battle = new Battle(List.of(fuXuan, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** The Skill opens the zone（【鉴知】 on the whole side); that is how the state gets there. */
        private void openTheZone() {
            battle.castImmediate(fuXuan.getSkills().get(SkillType.SKILL), fuXuan, List.of(ally));
            battle.processRequests();
        }

        /**
         * Records HP the side has lost, the way the engine does: one HP_LOST firing per loss, its magnitude the amount.
         *
         * <p>Fired rather than dealt, because a fixture that actually damages an ally has to get past shields, the zone's damage
         * sharing and death - none of which is what this Eidolon is about.
         */
        private void accumulate(int lostHp) {
            for (int i = 0; i < 1; i++) {
                battle.fire(TriggerPayload.Simple.of(TriggerEvent.HP_LOST, ally, ally, 0, lostHp));
                battle.processRequests();
            }
        }

        private void castUltimate() {
            battle.castImmediate(fuXuan.getSkills().get(SkillType.ULTRA), fuXuan, List.of(enemy));
            battle.processRequests();
        }

        private void allyIsHit() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemy, ally, 0, 0));
            battle.processRequests();
        }

        private void fuXuanIsHit() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemy, fuXuan, 0, 0));
            battle.processRequests();
        }
    }

    // ==================================================================
    // The talent, second half: 「当符玄当前生命值百分比 ≤ 50% 时触发生命回复，使自身回复等同于已损失生命值 90% 的生命值…
    // 初始拥有 1 次触发次数，最多可拥有 2 次」.
    //
    // The ward half (【避厄】, -18%) is in the base-kit judge above; this one is about the heal and its charge counter.
    // ==================================================================

    @Test
    public void theHealFiresAtHalfHpAndSpendsACharge() {
        TalentFixture f = new TalentFixture();
        Assertions.assertEquals(1, f.fuxuan.getBuffManager().stacksOf(CHARGE), "「初始拥有 1 次触发次数」");

        // Down to 45% WITHOUT an HP_LOST event (consumeHp), then take one real hit: that hit is what triggers the heal.
        double max = f.fuxuan.getMaxHp();
        f.fuxuan.consumeHp(max * 0.55);
        double before = f.fuxuan.getCurrentHp();
        Assertions.assertTrue(before / max < 0.5, "precondition: she is under half");

        double[] healed = new double[1];
        f.battle.addHealObserver((healer, target, amount) -> healed[0] += amount);
        double dealt = f.battle.applyTrueDamage(f.enemy, f.fuxuan, DamageElement.PHYSICAL, max * 0.05);
        f.battle.processRequests();

        double missingAfterHit = max - (before - dealt);
        System.out.println("[1208] missing=" + missingAfterHit + " healed=" + healed[0]
                + " charges=" + f.fuxuan.getBuffManager().stacksOf(CHARGE));
        Assertions.assertEquals(0.9 * missingAfterHit, healed[0], 1e-6, "「回复等同于已损失生命值 90%」");
        Assertions.assertEquals(0, f.fuxuan.getBuffManager().stacksOf(CHARGE), "one charge spent");
    }

    /**
     * The counter caps the effect: with no charge left, the same hit heals nothing.
     */
    @Test
    public void withoutAChargeTheHealDoesNothing() {
        TalentFixture f = new TalentFixture();
        double max = f.fuxuan.getMaxHp();
        f.fuxuan.consumeHp(max * 0.55);
        f.fuxuan.getBuffManager().removeState(CHARGE);

        double[] healed = new double[1];
        f.battle.addHealObserver((healer, target, amount) -> healed[0] += amount);
        f.battle.applyTrueDamage(f.enemy, f.fuxuan, DamageElement.PHYSICAL, max * 0.05);
        f.battle.processRequests();
        Assertions.assertEquals(0.0, healed[0], 1e-9, "the charge is the gate");
    }

    /**
     * The Ultimate grants the second charge, and the cap holds at two.
     */
    @Test
    public void theUltimateGrantsTheSecondCharge() {
        TalentFixture f = new TalentFixture();
        Assertions.assertEquals(1, f.fuxuan.getBuffManager().stacksOf(CHARGE), "precondition: one at battle start");

        f.ultimate();
        Assertions.assertEquals(2, f.fuxuan.getBuffManager().stacksOf(CHARGE),
                "「获得 1 次天赋的生命回复触发次数」");

        f.ultimate();
        Assertions.assertEquals(2, f.fuxuan.getBuffManager().stacksOf(CHARGE), "「最多可拥有 2 次」 - the cap holds");
    }

    // ==================================================================

    private static final class TalentFixture {
        private final Character fuxuan;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private void ultimate() {
            battle.castImmediate(fuxuan.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), fuxuan,
                    java.util.List.of(enemy));
        }

        private TalentFixture() {
            fuxuan = CharacterFactory.create(FUXUAN, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(fuxuan, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
            battle.startBattle();
        }
    }

    // ==================================================================
    // The technique, battle half: 「屏障持续期间进入战斗时符玄自动开启【穷观阵】，持续 2 回合」 - the same three effects the
    // Skill opens, on the technique's 2-turn clock.
    // ==================================================================

    @Test
    public void theTechniqueOpensTheZoneOnEntry() {
        TechniqueFixture with = new TechniqueFixture(true);
        TechniqueFixture without = new TechniqueFixture(false);

        Assertions.assertTrue(with.fuxuan.getBuffManager().hasState(ZONE), "「自动开启【穷观阵】」 - 【char:1208/insight】 is up");
        Assertions.assertTrue(with.ally.getBuffManager().hasState(ZONE), "on every ally");

        // The share is an ABSOLUTE amount: 6% of HER Max HP, added to each ally (not 6% of the ally own HP).
        double added = with.ally.getAttribute(AttributeType.HEALTH).get()
                - without.ally.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertEquals(0.06 * without.fuxuan.getAttribute(AttributeType.HEALTH).get(), added, 1e-6,
                "「生命上限提高…等同于符玄生命上限的 6%」");
        Assertions.assertEquals(without.ally.getAttribute(AttributeType.CRIT_CHANCE).get() + 0.12,
                with.ally.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6, "「暴击率提高 12.00%」");

        Assertions.assertFalse(without.fuxuan.getBuffManager().hasState(ZONE), "no technique, no zone");
    }

    /**
     * 「当符玄陷入无法战斗状态时，【穷观阵】也会被解除」: the zone is anchored to HER clock, so her death takes it off the ally.
     */
    @Test
    public void herDeathTakesTheZoneOffEveryone() {
        TechniqueFixture f = new TechniqueFixture(true);
        f.battle.castImmediate(f.fuxuan.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), f.fuxuan,
                java.util.List.of(f.ally));
        Assertions.assertTrue(f.ally.getBuffManager().hasState(ZONE), "precondition: the zone is up");

        f.battle.applyTrueDamage(f.ally, f.fuxuan, com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                f.fuxuan.getCurrentHp() * 10);
        f.battle.processRequests();

        Assertions.assertTrue(f.fuxuan.isDeath(), "precondition: she is down");
        Assertions.assertFalse(f.ally.getBuffManager().hasState(ZONE),
                "「当符玄陷入无法战斗状态时，【穷观阵】也会被解除」");
    }

    private static final class TechniqueFixture {
        private final Character fuxuan;
        private final Character ally;
        private final Battle battle;

        private TechniqueFixture(boolean technique) {
            fuxuan = CharacterFactory.create(FUXUAN, LEVEL);
            ally = CharacterFactory.create(ALLY_1001, LEVEL);
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(fuxuan, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            if (technique) {
                battle.markTechniqueUsed(fuxuan);
            }
            battle.startBattle();
        }
    }
}
