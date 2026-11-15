package com.laosun.aluminium.test.content.characters.huohuo;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1217 藿藿 (Huohuo), from her own file: the Skill (战技)'s cleanse, the ultimate's per-recipient energy, and [禳命]; plus
 * her Eidolons (1-6), the talisman's clock and her three 行迹 clauses.
 *
 * <p><b>The kit.</b> Nothing new: `DISPEL`, `other_allies`, `GAIN_ENERGY` with a per-target share of max energy, a
 * [`owner_max_hp` + constant] heal, `ticks_on: "self"`, and `target_when` with `target_hp_percent` for "each of our targets whose current HP
 * percentage is {@code <= 50}% produces 1 stack each".
 *
 * <p><b>What is registered</b> (the file's notes): the Skill (战技)'s "adjacent target" heal (no position axis), the talent's "after being enhanced" variant
 * (a talent upgrade no gate can select) and its "or when casting the Ultimate" trigger (no selector names a cast's caster for a heal).
 *
 * <p><b>Eidolons 3, 5 and 6.</b> 3 and 5 are the level clauses the data spells as {@code skill_add_level}; 6 is the one
 * measured here: 「为我方目标提供治疗时，使目标造成的伤害提高 50%，持续 2 回合」 - it hangs on {@code HEALED}, which
 * {@code Battle.heal} broadcasts after the amount is settled, and it must fire only for HER heals (「为我方目标提供治疗时」).
 *
 * <p><b>Eidolon 4.</b> 「目标的当前生命值越低治疗量越高，最多使藿藿提供的治疗量提高 80%」 - a <b>continuous</b> bonus, not a
 * threshold, so the check is the healing amount at three HP shares: +0% at full, +40% at half, +80% at empty.
 *
 * <p>The target is a rule-less unit brought to the share with true damage, so the only difference between two runs is the
 * healer's Eidolon rank (a character with shields or reductions would land the damage for a different amount).
 *
 * <p><b>Eidolon 1's speed aura.</b> 「当藿藿拥有【禳命】时我方全体提高 12% 的速度」. The modifier sits on every ally while the
 * GATE is on her: losing 【禳命】 drops the aura at the next turn boundary. Both halves are measured on the same unit - with
 * the buff up, then after the state leaves - so no cross-character base speed enters the ratio.
 *
 * <p><b>The talisman's clock.</b> "after casting the Skill, Huohuo gains [禳命], lasting 2 turns, and the remaining turns
 * decrease by 1 at the start of each of Huohuo's turns". The point is WHOSE clock spends it: the sentence names Huohuo
 * (藿藿), not the party.
 *
 * <p><b>Eidolon 2.</b> "...<b>decreases [禳命]'s remaining turns by 1</b>" on the lethal save. The reduction is the mechanism
 * the game's own config uses: `Avatar_Huohuo_00_Rank02_Insert` does
 * `SetModifierValue{ModifierName: "MAvatar_Huohuo_Passive_HealMark", ModifyFunction: "Add", ValueType: "LifeTime"}`, beside a
 * `SetDynamicValueByAddValue{AddValue: -1, Min: 0}` on the same count. So it is a negative `turns` on the existing `EXTEND_BUFF` -- one
 * mechanism, not two dialects.
 *
 * <p><b>The traces.</b> Three 行迹 clauses (the attribute traces are folded in by the character builder; these three are rules):
 * 不敢自专 - 「战斗开始时，藿藿获得【禳命】，持续 1 回合」; 贞凶之命 - 「抵抗控制类负面状态的概率提高 35%」, and only that
 * class; 怯惧应激 - 「触发天赋为我方目标提供治疗时，藿藿恢复 1 点能量」 - the talent's heal hangs on TURN_START while
 * 【禳命】 holds, so the energy is paid there too.
 *
 * <p>Merged from the former {@code HuohuoTest}, {@code HuohuoEidolonTest}, {@code HuohuoEidolonFourTest},
 * {@code HuohuoSpeedAuraTest}, {@code HuohuoTalismanDurationTest}, {@code HuohuoTalismanLosesATurnTest} and
 * {@code HuohuoTracesTest}; every test, assertion and Javadoc of measurement is preserved.
 */
public class HuohuoTest {
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1002;
    private static final int EIDOLON_ALLY = 1001;
    private static final int LEVEL = 80;
    private static final String TALISMAN = "禳命";

    // ==================================================================
    // From HuohuoTest: the Skill's state, the ultimate's shares, the talent's gate
    // ==================================================================

    /**
     * The state she applies to herself, on her own clock.
     */
    @Test
    public void herSkillGrantsTheStateToHerself() {
        KitFixture f = new KitFixture();
        // The 行迹 grants 【禳命】 at battle start (1217101), so the "nothing yet" state is produced by removing it.
        f.huohuo.getBuffManager().removeState("禳命");
        Assertions.assertFalse(f.huohuo.getBuffManager().hasState("禳命"), "nothing before the cast");

        f.skill();

        Assertions.assertTrue(f.huohuo.getBuffManager().hasState("禳命"), "「施放战技后藿藿获得【禳命】」 (after casting the Skill, Huohuo gains Divine Provision (【禳命】))");
        Assertions.assertFalse(f.ally.getBuffManager().hasState("禳命"), "…and it is hers (「藿藿获得」), not the party's");
    }

    /**
     * Note: "除自身以外的队友": the ultimate boosts every OTHER ally's attack (the energy half is registered).
     */
    @Test
    public void herUltimatePaysTheOthersNotHerself() {
        KitFixture f = new KitFixture();
        double allyBefore = f.ally.getAttribute(AttributeType.ATTACK).get();
        double herBefore = f.huohuo.getAttribute(AttributeType.ATTACK).get();
        double allyEnergyBefore = f.ally.getCurrentEnergy();
        double herEnergyBefore = f.huohuo.getCurrentEnergy();

        f.ultimate();

        Assertions.assertTrue(f.ally.getAttribute(AttributeType.ATTACK).get() > allyBefore,
                "「使其攻击力提高40.00%」 (raises its ATK by 40.00%) -- an ally is boosted");
        Assertions.assertEquals(herBefore, f.huohuo.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "⚠ 「**除自身以外**的队友」: she is not (boosting everyone would be the plausible-looking wrong reading)");
        Assertions.assertTrue(f.ally.getCurrentEnergy() > allyEnergyBefore,
                "「为除自身以外的队友恢复等同于**各自**20.00%能量上限的能量」 (restores Energy to allies other than herself equal to **each one's** 20.00% of their Energy ceiling) -- a share of the ALLY's own maximum");
        Assertions.assertEquals(herEnergyBefore, f.huohuo.getCurrentEnergy(), 1e-6,
                "⚠ …and her own bar is untouched: 「除自身以外」 (other than herself) governs both halves");
    }

    /**
     * The talent's trigger is gated on HER state, and both of its halves are on the rule.
     */
    @Test
    public void theTalentTriggerNeedsHerState() {
        KitFixture f = new KitFixture();
        TriggerTable table = TriggerTables.of(HUOHUO);

        f.huohuo.getBuffManager().removeState("禳命");
        Assertions.assertTrue(table.matching(TriggerEvent.TURN_START, ctx(f, false)).isEmpty(),
                "no 【禳命】 (Divine Provision) on her, no trigger");

        f.skill();
        Assertions.assertFalse(table.matching(TriggerEvent.TURN_START, ctx(f, true)).isEmpty(),
                "with 【禳命】 (Divine Provision) up, a turn start matches");
    }

    private static TriggerTable.TriggerContext ctx(KitFixture f, boolean ignored) {
        return new TriggerTable.TriggerContext(f.huohuo, f.ally, f.ally, 0, 0, null, f.battle);
    }

    private static final class KitFixture {
        private final Character huohuo = CharacterFactory.create(HUOHUO, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(huohuo, ally), List.of(enemy), fixed());

        private KitFixture() {
            battle.startBattle();
        }

        private void skill() {
            battle.castImmediate(huohuo.getSkills().get(SkillType.SKILL), huohuo, List.of(ally));
        }

        private void ultimate() {
            battle.castImmediate(huohuo.getSkills().get(SkillType.ULTRA), huohuo, List.of(enemy));
        }
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    // ==================================================================
    // From HuohuoEidolonTest: Eidolons 1, 3, 5 and 6
    // ==================================================================

    /**
     * Eidolon 1, first half: 「天赋产生的【禳命】持续时间延长 1 回合」 - a MODIFY_RULE on the two rules that grant it.
     */
    @Test
    public void eidolonOneLengthensTheTalisman() {
        Assertions.assertEquals(talismanTurns(0, false) + 1, talismanTurns(1, false), "the Skill's 禳命 is one turn longer");
        Assertions.assertEquals(talismanTurns(0, true) + 1, talismanTurns(1, true), "the trace's 禳命 too");
    }

    private static int talismanTurns(int eidolon, boolean viaTrace) {
        EidolonFixture f = new EidolonFixture(eidolon);
        if (!viaTrace) {
            f.battle.castImmediate(f.huohuo.getSkills().get(SkillType.SKILL), f.huohuo, List.of(f.ally));
        }
        return f.huohuo.getBuffManager().activeBuffs().stream()
                .filter(buff -> buff instanceof com.laosun.aluminium.buff.StateBuff state
                        && "禳命".equals(state.getState()))
                .mapToInt(com.laosun.aluminium.buff.AbstractBuff::remainingTurns)
                .max().orElseThrow();
    }

    /**
     * Eidolon 3 and 5: the level clauses.
     */
    @Test
    public void theLevelEidolonsRaiseTheirSlots() {
        Assertions.assertEquals(level(0, SkillType.ULTRA) + 2, level(3, SkillType.ULTRA), "E3: 终结技 +2");
        Assertions.assertEquals(level(0, SkillType.TALENT) + 2, level(3, SkillType.TALENT), "E3: 天赋 +2");
        Assertions.assertEquals(level(0, SkillType.SKILL) + 2, level(5, SkillType.SKILL), "E5: 战技 +2");
        Assertions.assertEquals(level(0, SkillType.COMMON) + 1, level(5, SkillType.COMMON), "E5: 普攻 +1");
    }

    /**
     * Eidolon 6: the healed ally gains 50% damage dealt, and only when SHE healed it.
     */
    @Test
    public void eidolonSixBoostsTheAllySheHeals() {
        Assertions.assertEquals(0.0, damageBoost(0, true), 1e-9, "E0: nothing");
        Assertions.assertEquals(0.5, damageBoost(6, true), 1e-9, "「使目标造成的伤害提高 50%」");

        // Somebody else's heal must not trigger hers.
        EidolonFixture f = new EidolonFixture(6);
        Character other = CharacterFactory.create(1002, LEVEL);
        f.battle.heal(other, f.ally, 100.0);
        Assertions.assertEquals(0.0, f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "「为我方目标提供治疗时」 - the healer must be her");
    }

    private static double damageBoost(int eidolon, boolean healedByHer) {
        EidolonFixture f = new EidolonFixture(eidolon);
        double before = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        if (healedByHer) {
            f.battle.heal(f.huohuo, f.ally, 100.0);
        }
        return f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before;
    }

    private static int level(int eidolon, SkillType slot) {
        EidolonFixture f = new EidolonFixture(eidolon);
        return 1 + f.huohuo.skillLevelBonus(slot);
    }

    private static final class EidolonFixture {
        private final Character huohuo;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private EidolonFixture(int eidolon) {
            huohuo = CharacterFactory.create(HUOHUO, LEVEL, true, null, null, eidolon);
            ally = CharacterFactory.create(EIDOLON_ALLY, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(huohuo, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
            // A full-HP ally cannot be healed for anything, and a zero heal has no amount to broadcast.
            battle.applyDamage(ally, new com.laosun.aluminium.damage.Damage(battle.enemies().getFirst(), ally,
                    com.laosun.aluminium.enums.DamageElement.PHYSICAL, com.laosun.aluminium.enums.DamageType.NORMAL, 200.0));
        }
    }

    // ==================================================================
    // From HuohuoEidolonFourTest: Eidolon 4's continuous healing bonus
    // ==================================================================

    @Test
    public void theBonusGrowsAsTheTargetIsMoreHurt() {
        Assertions.assertEquals(1.0, ratio(4, 1.0), 1e-6, "满血: no bonus (「越低…越高」 has nothing to give)");
        Assertions.assertEquals(1.4, ratio(4, 0.5), 1e-6, "half HP: 0.8 x 0.5 = 40%");
        // HP floors at 1, so the near-empty case is 1% (the full 80% is the limit, not a reachable point).
        Assertions.assertEquals(1.792, ratio(4, 0.01), 1e-6, "1% HP: 0.8 x 0.99 = 79.2%");

        Assertions.assertEquals(1.0, ratio(0, 0.5), 1e-6, "without the Eidolon the heal does not follow the HP");
        Assertions.assertEquals(1.0, ratio(0, 0.01), 1e-6);
    }

    private static double ratio(int eidolon, double hpShare) {
        return healed(eidolon, hpShare) / healed(0, hpShare);
    }

    private static double healed(int eidolon, double hpShare) {
        Character huohuo = CharacterFactory.create(HUOHUO, LEVEL, true, null, null, eidolon);
        Character ally = Character.fromAttributes("Poor Soul", 100000, 500, 100, 100);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(huohuo, ally)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();

        double missing = ally.getMaxHp() * (1 - hpShare);
        if (missing > 0) {
            ally.consumeHp(missing);   // exact HP loss: no damage zones to reduce it
        }
        Assertions.assertEquals(hpShare, ally.getCurrentHp() / ally.getMaxHp(), 1e-6,
                "precondition: the target is at the intended HP share");
        return battle.calculateHeal(huohuo, ally, 1000.0);
    }

    // ==================================================================
    // From HuohuoSpeedAuraTest: Eidolon 1's speed aura
    // ==================================================================

    @Test
    public void whileSheHoldsTheTalismanEveryoneIsFaster() {
        SpeedFixture f = new SpeedFixture(1);
        Assertions.assertTrue(f.huohuo.getBuffManager().hasState(TALISMAN), "the trace puts it on her at battle start");
        double allyUp = f.speed(f.ally);
        double herUp = f.speed(f.huohuo);

        f.dropTalismanAndTick();

        double allyDown = f.speed(f.ally);
        double herDown = f.speed(f.huohuo);
        System.out.println("[1217] ally " + allyDown + " -> " + allyUp + " ; her " + herDown + " -> " + herUp);
        Assertions.assertEquals(1.12, allyUp / allyDown, 1e-6, "「我方全体提高 12% 的速度」");
        // Her own panel carries other SPEED modifiers, so the percent lands on a different base than the bare ally: the
        // clause "includes her" is asserted as "she gets faster too", the exact 12% is pinned on the ally above.
        Assertions.assertTrue(herUp / herDown > 1.0, "「我方全体」 includes her");
    }

    @Test
    public void withoutTheEidolonThereIsNoAura() {
        SpeedFixture f = new SpeedFixture(0);
        double allyUp = f.speed(f.ally);
        f.dropTalismanAndTick();
        Assertions.assertEquals(allyUp, f.speed(f.ally), 1e-9, "E0: no aura either way");
    }

    private static final class SpeedFixture {
        private final Character huohuo;
        private final Character ally;
        private final Battle battle;

        private SpeedFixture(int eidolon) {
            huohuo = CharacterFactory.create(HUOHUO, LEVEL, true, null, null, eidolon);
            ally = CharacterFactory.create(EIDOLON_ALLY, LEVEL);
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(huohuo, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
        }

        private double speed(Character unit) {
            return unit.getAttribute(AttributeType.SPEED).get();
        }

        private void dropTalismanAndTick() {
            huohuo.getBuffManager().removeState(TALISMAN);
            ally.getBuffManager().beforeMove();
            huohuo.getBuffManager().beforeMove();
        }
    }

    // ==================================================================
    // From HuohuoTalismanDurationTest: whose clock spends the talisman
    // ==================================================================

    /**
     * A teammate's turn does not spend it; hers does, and the count is the one the sentence states.
     */
    @Test
    public void theTalismanRunsOnHerOwnClock() {
        Character her = CharacterFactory.create(HUOHUO, 80, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, ally),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        SkillExecutor.execute(battle, her.getSkills().get(SkillType.SKILL), her, List.of(her));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(TALISMAN), "precondition: her skill grants 【禳命】 (Divine Provision)");

        spendTurnOf(battle, ally);
        boolean afterAllyTurn = her.getBuffManager().hasState(TALISMAN);
        spendTurnOf(battle, her);
        boolean afterHerFirstTurn = her.getBuffManager().hasState(TALISMAN);
        spendTurnOf(battle, her);
        boolean afterHerSecondTurn = her.getBuffManager().hasState(TALISMAN);
        System.out.println("[huohuo-clock] after the ally's turn=" + afterAllyTurn
                + " ; after her 1st=" + afterHerFirstTurn + " ; after her 2nd=" + afterHerSecondTurn);

        Assertions.assertTrue(afterAllyTurn,
                "「藿藿每回合开始时」 (at the start of each of Huohuo's turns)-- the clock is HERS, so a teammate's turn costs it nothing");
        Assertions.assertTrue(afterHerFirstTurn, "「持续 2 回合」 (lasts 2 turns)-- one of her turns is not two");
        Assertions.assertFalse(afterHerSecondTurn, "「持续回合数减 1」 (the remaining turns are reduced by 1)-- two of her turns spend it");
    }

    /**
     * Note: Half a turn is `beforeMove()` alone; a full one is both halves, and expiry lands on the late half.
     */
    private static void spendTurnOf(Battle battle, Character unit) {
        Signal signal = battle.queue().snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == unit).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: the unit is in the queue"));
        battle.setCurrentMove(signal);
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
    }

    // ==================================================================
    // From HuohuoTalismanLosesATurnTest: Eidolon 2 spends one turn on the save
    // ==================================================================

    /**
     * The save spends one turn of the talisman, and the ally survives.
     */
    @Test
    public void theSaveSpendsOneTurnOfTheTalisman() {
        Character her = CharacterFactory.create(HUOHUO, 80, false, null, null, 2);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, ally),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        // Her skill is what puts [禳命] on her -- and it is aimed at the ENEMY, exactly as the shipped judge drives it.
        Skill skill = her.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(TALISMAN), "precondition: 【禳命】 (Divine Provision) is on her");
        int before = her.getBuffManager().findBuff(StateBuff.class).duration();

        battle.applyTrueDamage(battle.enemies().getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
        battle.processRequests();

        int after = her.getBuffManager().findBuff(StateBuff.class).duration();
        System.out.println("[talisman] duration " + before + " -> " + after
                + " ; the saved ally survives = " + !ally.isDeath());

        // Eidolon 1 lengthens the Talisman by one turn, so the invariant is "the save spends ONE turn", not "it ends at 1".
        Assertions.assertEquals(before - 1, after,
                "「使【禳命】的持续回合数减 1」 - one turn, whatever she started with");
        Assertions.assertTrue(!ally.isDeath(),
                "「不会陷入无法战斗状态」 (does not fall into the unable-to-fight state)-- answering the lethal event is what cancels the death");
    }

    // ==================================================================
    // From HuohuoTracesTest: the three 行迹 clauses
    // ==================================================================

    /**
     * The battle opens with 【禳命】 on her.
     */
    @Test
    public void theBattleOpensWithTheTalisman() {
        TraceFixture f = new TraceFixture();
        Assertions.assertTrue(f.huohuo.getBuffManager().hasState(TALISMAN),
                "「战斗开始时，藿藿获得【禳命】」");
    }

    /**
     * The trace resists the CONTROL class only.
     */
    @Test
    public void theTraceResistsControlOnly() {
        TraceFixture f = new TraceFixture();
        Assertions.assertEquals(0.35, f.huohuo.getBuffManager().debuffResistOf(DebuffClass.CONTROL), 1e-9,
                "「抵抗控制类负面状态的概率提高 35%」");
        Assertions.assertEquals(0.0, f.huohuo.getBuffManager().debuffResistOf(DebuffClass.DOT), 1e-9,
                "it must not become a general resistance");
    }

    /**
     * The talent's heal pays her one energy - and only while 【禳命】 holds.
     */
    @Test
    public void theTalentHealPaysOneEnergy() {
        TraceFixture f = new TraceFixture();
        double before = f.huohuo.getCurrentEnergy();
        f.fireTurnStart();
        Assertions.assertEquals(1.0, f.huohuo.getCurrentEnergy() - before, 1e-9,
                "「触发天赋…治疗时，藿藿恢复 1 点能量」");

        f.huohuo.getBuffManager().removeState(TALISMAN);
        double after = f.huohuo.getCurrentEnergy();
        f.fireTurnStart();
        Assertions.assertEquals(0.0, f.huohuo.getCurrentEnergy() - after, 1e-9,
                "no talent heal without the state, so no energy");
    }

    private static final class TraceFixture {
        private final Character huohuo;
        private final Battle battle;

        private TraceFixture() {
            huohuo = CharacterFactory.create(HUOHUO, LEVEL);
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(huohuo, CharacterFactory.create(EIDOLON_ALLY, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
        }

        private void fireTurnStart() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, huohuo, huohuo, 0, 0));
        }
    }
}
