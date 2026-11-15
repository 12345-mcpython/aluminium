package com.laosun.aluminium.test.content.characters.bailu;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.ReductionBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1211 白露 clause coverage: every section of {@code 1211_白露.md}, one line each, plus the readings that pin the
 * clauses the machine guards cannot see.
 *
 * <p><b>Three things this file found (2026-11-02)</b>, in the order they matter:
 * <ol>
 *   <li><b>The Eidolon 1 rule paid the wrong unit.</b> It was written {@code self state_ended 生息} + {@code hp_percent >= 1} +
 *       {@code target: self} — both the "whose [生息] ended" and the "whose HP is full" ends of the sentence were read on
 *       <b>Bailu herself</b>, so she collected 8 energy whenever <i>she</i> was full. Measured three ways before changing
 *       anything (see {@link #eidolon1PaysTheFullHpCarrierAndNothingWhenHurt} and its E0 neighbour).</li>
 *   <li><b>The talent's "1 time per battle" had never been written</b>, so every lethal blow healed again. Now
 *       {@code once_per_battle: true}; the Eidolon 6 "one more time" is registered (a boolean cannot say "2").</li>
 *   <li><b>Eidolon 4 cannot ride {@code HEALED}</b>: the loader refuses {@code from_category BPSKILL} there ("HEALED carries
 *       none"). It is written on {@code SKILL_CAST} instead — equivalent today, because a skill heals exactly one target.</li>
 * </ol>
 *
 * <p>The rest of her document is registered in {@code 1211.json}'s {@code _gaps}; nothing is left silent.
 *
 * <p><b>L3:</b> the two readings of the Eidolon 1 sentence now sit in one scenario test over its one {@code Fixture(1,
 * false, true)} board, each assertion inside {@code Assertions.assertAll} with the message that names its clause. The E0
 * gate stays its own test: a different board is what that case is about.
 */
public class BailuClauseCoverageTest {

    private static final int BAILU = 1211;
    private static final int ALLY = 1002;

    /**
     * 「生命值/防御力/效果抵抗 +N%」: the stat traces are folded into the panel rather than written as rules.
     *
     * <p>Measured on 2026-11-02: HEALTH 1319.47 × 1.28 = <b>1688.92416</b> (+4+4+6+6+8 %), DEFENCE 485.1 × 1.225 =
     * <b>594.2475</b> (+5+7.5+10 %), EFFECT_RESISTANCE <b>0.1</b> (+4+6 %).
     */
    @Test
    public void theStatTracesAreFoldedIntoThePanel() {
        Character bailu = CharacterFactory.create(BAILU, 80);
        Assertions.assertEquals(1688.92416, bailu.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "HEALTH: the doc's Lv80 base 1319.47 plus the +28% of the four health traces");
        Assertions.assertEquals(594.2475, bailu.getAttribute(AttributeType.DEFENCE).get(), 1e-6,
                "DEFENCE: 485.1 plus the +22.5% of the three defence traces");
        Assertions.assertEquals(0.1, bailu.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), 1e-9,
                "EFFECT_RESISTANCE: the +4% and +6% traces");
    }

    /**
     * Eidolon 1 (「【生息】结束时若我方目标当前生命值等于其生命上限，则额外恢复目标8点能量」): <b>the carrier</b> is paid, not Bailu —
     * and the reverse half, 「若…生命值等于其生命上限」, pays nothing for an ally below full HP even though Bailu is full.
     *
     * <p>Both halves are the one clause on the same {@code Fixture(1, false, true)} board; the second only wounds the ally
     * first. Measured before the fix: with both at full HP the old rule gave <b>Bailu</b> +8 and the ally +0; with the ally
     * hurt it still paid Bailu 8.
     */
    @Test
    public void eidolon1PaysTheFullHpCarrierAndNothingWhenHurt() {
        Fixture full = new Fixture(1, false, true);
        double bailuBefore = full.bailu.getCurrentEnergy();
        double allyBefore = full.ally.getCurrentEnergy();

        full.battle.fireStateEnded(full.ally, "char:1211/lifespan");

        Assertions.assertAll("「【char:1211/lifespan】结束时若我方目标当前生命值等于其生命上限，则额外恢复目标8点能量」",
                () -> Assertions.assertEquals(8.0, full.ally.getCurrentEnergy() - allyBefore, 1e-9,
                        "「额外恢复目标 8 点能量」 — the target is the one whose [char:1211/lifespan] ended"),
                () -> Assertions.assertEquals(0.0, full.bailu.getCurrentEnergy() - bailuBefore, 1e-9,
                        "and it is NOT the rule's owner (the old reading paid Bailu)"));

        Fixture hurt = new Fixture(1, false, true);
        hurt.ally.takeDamage(hurt.ally.getCurrentHp() * 0.5);
        double hurtBailuBefore = hurt.bailu.getCurrentEnergy();
        double hurtAllyBefore = hurt.ally.getCurrentEnergy();

        hurt.battle.fireStateEnded(hurt.ally, "char:1211/lifespan");

        Assertions.assertAll("「若…生命值等于其生命上限」 — an ally below full HP pays nothing",
                () -> Assertions.assertEquals(0.0, hurt.ally.getCurrentEnergy() - hurtAllyBefore, 1e-9,
                        "the carrier is below full HP ⇒ nothing (measured: the old rule still paid Bailu 8 here)"),
                () -> Assertions.assertEquals(0.0, hurt.bailu.getCurrentEnergy() - hurtBailuBefore, 1e-9,
                        "...and nothing to Bailu either"));
    }

    /**
     * And the gate: it is Eidolon 1, so at E0 the clause is not live.
     */
    @Test
    public void eidolon1IsNotLiveAtE0() {
        Fixture f = new Fixture(0, false, true);
        double allyBefore = f.ally.getCurrentEnergy();

        f.battle.fireStateEnded(f.ally, "char:1211/lifespan");

        Assertions.assertEquals(0.0, f.ally.getCurrentEnergy() - allyBefore, 1e-9,
                "「星魂 1」 gates the clause itself");
    }

    /**
     * 秘技: 「下一次战斗开始时为我方全体附上【生息】，持续 2 回合」 — and the trace's −10% rides with it.
     */
    @Test
    public void theTechniqueInvigoratesTheWholePartyAtBattleStart() {
        Fixture with = new Fixture(0, true, false);
        Assertions.assertTrue(with.bailu.getBuffManager().hasState("char:1211/lifespan"), "「我方全体」 includes Bailu");
        Assertions.assertTrue(with.ally.getBuffManager().hasState("char:1211/lifespan"), "...and the ally");
        Assertions.assertEquals(1, reductionsOn(with.ally),
                "「拥有【char:1211/lifespan】的角色受到的伤害降低 10%」 — the technique's grant carries the trace too");

        Fixture without = new Fixture(0, false, false);
        Assertions.assertFalse(without.ally.getBuffManager().hasState("char:1211/lifespan"),
                "control: no technique, no ultimate ⇒ no [char:1211/lifespan] (so the reading above is about the technique)");
        Assertions.assertEquals(0, reductionsOn(without.ally), "control: no reduction either");
    }

    /**
     * Eidolon 4: 「战技提供的每1次治疗…造成的伤害提高10%，最多叠加3层，持续2回合」.
     *
     * <p>Measured: one cast = +0.1, two = +0.2, three = +0.3, four = <b>still</b> +0.3 (the cap), and at E3 nothing at all.
     */
    @Test
    public void eidolon4StacksOncePerSkillCast() {
        Fixture e4 = new Fixture(4, false, true);
        double[] expected = {0.1, 0.2, 0.3, 0.30000000000000004};
        for (int cast = 0; cast < expected.length; cast++) {
            e4.castSkillOnAlly();
            Assertions.assertEquals(expected[cast], e4.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(),
                    1e-9, "cast " + (cast + 1) + ": one stack per heal, capped at 3");
        }

        Fixture e3 = new Fixture(3, false, true);
        e3.castSkillOnAlly();
        Assertions.assertEquals(0.0, e3.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "control: E3 ⇒ the clause is not live");
    }

    /**
     * 天赋: 「该效果单场战斗中可以触发 1 次」.
     *
     * <p>The first lethal blow leaves the victim standing; the second must <b>not</b> be answered again (measured: HP 0).
     * Note: without {@code once_per_battle} this healed on every lethal blow — the registered symptom this pins.
     */
    @Test
    public void theLethalHealHappensOncePerBattle() {
        Fixture f = new Fixture(0, false, true);

        f.strikeLethally();
        Assertions.assertTrue(f.ally.getCurrentHp() > 0, "precondition: the first lethal blow was answered");
        double afterFirst = f.ally.getCurrentHp();

        f.strikeLethally();
        Assertions.assertEquals(0.0, f.ally.getCurrentHp(), 1e-9,
                "「单场战斗中可以触发 1 次」 — the second blow is not healed (first heal left " + afterFirst + " HP)");
    }

    /**
     * 星魂 6: 「效果触发次数增加 1 次」 — the second lethal blow IS answered at E6.
     *
     * <p>The allowance is a counter rather than {@code once_per_battle} precisely so this Eidolon could raise it: a boolean has
     * nothing to add 1 to.
     */
    @Test
    public void eidolonSixAnswersASecondLethalBlow() {
        Fixture f = new Fixture(6, false, true);

        f.strikeLethally();
        Assertions.assertTrue(f.ally.getCurrentHp() > 0, "precondition: the first lethal blow was answered");

        f.strikeLethally();
        Assertions.assertTrue(f.ally.getCurrentHp() > 0,
                "「效果触发次数增加 1 次」 — the second blow is answered too at E6");
    }

    /**
     * 天赋: 「拥有【生息】的我方目标受到攻击后回复 5.4% 生命上限 + 144，该效果可以触发 2 次」.
     *
     * <p>The 「2 次」 is a counted allowance read off the talent's own row (#5), and the third hit heals nobody. That is the count the
     * gap called missing — it never needed a state that carries its own uses.
     */
    @Test
    public void theInvigorationHealsTheHitAllyTwice() {
        Character bailu = CharacterFactory.create(BAILU, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(bailu, ally), List.of(BattleFixture.dummy()),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        battle.castImmediate(bailu.getSkills().get(SkillType.ULTRA), bailu, List.of(ally, bailu));
        battle.processRequests();
        Assertions.assertTrue(ally.getBuffManager().hasState("char:1211/lifespan"), "precondition: the Ultimate grants char:1211/lifespan");

        double first = ally.getCurrentHp();
        hit(battle, ally);
        double afterFirst = ally.getCurrentHp();
        hit(battle, ally);
        double afterSecond = ally.getCurrentHp();
        hit(battle, ally);
        double afterThird = ally.getCurrentHp();

        Assertions.assertTrue(afterFirst > first - 1e-6, "the first hit is answered");
        Assertions.assertTrue(afterSecond > afterFirst - 1e-6, "the second hit is answered too");
        Assertions.assertEquals(afterSecond, afterThird, 1e-9,
                "「该效果可以触发 2 次」 — the third hit is not healed");
        Assertions.assertEquals(0, bailu.getBuffManager().stacksOf("internal:1211/lifespan_heal_allowance"), "the allowance is spent");
    }

    private static void hit(Battle battle, Character ally) {
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.TAKING_HIT, battle.enemies().getFirst(), ally, 0, 0));
        battle.processRequests();
    }
    // ==================================================================
    // Helpers
    // ==================================================================

    private static int reductionsOn(Character character) {
        return character.getBuffManager().allBuffsOf(ReductionBuff.class).size();
    }

    private static final class Fixture {
        private final Character bailu;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Battle battle;

        /**
         * @param eidolon   the Eidolon rank Bailu is built at
         * @param technique whether her technique was used (the state the ultimate-less opening reads)
         * @param ultimate  whether to cast the ultimate once after the battle starts (the other source of [生息])
         */
        private Fixture(int eidolon, boolean technique, boolean ultimate) {
            bailu = CharacterFactory.create(BAILU, 80, false, null, null, eidolon);
            if (technique) {
                bailu.getBuffManager().addBuff(new StateBuff("sys:technique", 1));
            }
            battle = new Battle(List.of(bailu, ally), List.of(BattleFixture.dummy()),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
            if (ultimate) {
                battle.castImmediate(bailu.getSkills().get(SkillType.ULTRA), bailu, List.of(ally, bailu));
            }
            // Note: her Ultimate heals, and a heal at full HP now OVERFLOWS - which the trace 岐黄精义 turns into +10% Max HP. The
            // ally is therefore no longer at 100% afterwards, and 「【生息】结束时若生命值等于其上限」 would read false. Top up, so
            // the precondition the E1 cases need (full HP) really holds.
            ally.heal(ally.getMaxHp());
        }

        private void castSkillOnAlly() {
            battle.castImmediate(bailu.getSkills().get(SkillType.SKILL), bailu, List.of(ally));
        }

        private void strikeLethally() {
            battle.applyTrueDamage(battle.enemies().getFirst(), ally, DamageElement.ICE,
                    ally.getMaxHp() * 2.0);
            battle.processRequests();
        }
    }
}



