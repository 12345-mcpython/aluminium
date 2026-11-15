package com.laosun.aluminium.test.content.characters.sunday;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1313 星期日's Eidolons, as far as the document's sentences can be written today: 星魂 1's two defence-ignore shares,
 * 星魂 2's one-time skill points and damage share, 星魂 4's energy at his turn start, and 星魂 6's stacking talent.
 *
 * <p>星魂 1:「施放战技时，使目标角色造成伤害时无视目标 16% 的防御力，召唤物造成伤害时无视目标 40% 的防御力，持续 2 回合」.
 * Two DIFFERENT shares on two different units, which is why the engine grew a `target_summon` selector: the ally alone
 * takes 16% and its memosprite alone takes 40%. Measured with a memosprite-carrying ally so both halves exist at once.
 *
 * <p>星魂 2:「首次施放终结技后恢复 2 个战技点。【蒙福者】造成的伤害提高 30%」. Two halves: the skill point is a ONE-TIME
 * payment (a marker state spends it), and the damage share rides the Beatified state it is granted with.
 *
 * <p>星魂 4「回合开始时，恢复 8 点能量」: his own turns only.
 *
 * <p>星魂 6, the half that is writable:「天赋的暴击率提高效果可以叠加，最多叠加 3 层，并使天赋的持续时间增加 1 回合」.
 * Below 星魂 6 the talent REPLACES its modifier (one cast, one benefit); from 星魂 6 on it STACKS to three and lasts 4 turns
 * instead of 3. The third sentence of the same Eidolon (overflow crit Rate turned into CRIT DMG) is registered - it needs a
 * modifier derived from another attribute at settlement time, which the engine does not have.
 *
 * <p><b>L3 consolidation.</b> The cases that shared one fixture, one clause and one observed object are now ONE scenario
 * test, their assertions inside {@link Assertions#assertAll} with a short tag naming the clause (星魂 2's two halves on the
 * one Ultimate board, 星魂 6's stack count and duration on the talent's CRIT Rate modifier). Every expected value,
 * tolerance and message is the one the separate case carried.
 */
public class SundayEidolonTest {
    private static final int SUNDAY = 1313;
    private static final int MEMOSPRITE_ALLY = 1402;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    // ==================================================================
    // 星魂 1: the pair's defence ignore
    // ==================================================================

    @Test
    public void theSkillGivesThePairDifferentDefenceIgnore() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL, false, null, null, 1);
        Character ally = CharacterFactory.create(MEMOSPRITE_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        // 阿格莱雅 brings 衣匠 out with her Ultimate, so the ally really has a summon to aim at.
        battle.castImmediate(ally.getSkills().get(SkillType.ULTRA), ally, List.of(BattleFixture.dummy()));
        battle.processRequests();
        Summon memosprite = battle.summonOf(ally);
        Assertions.assertNotNull(memosprite, "precondition: the ally has a memosprite");

        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();

        Assertions.assertEquals(0.16, ally.getAttribute(AttributeType.DEFENCE_IGNORE).get(), EPS,
                "「目标角色…无视 16% 防御力」");
        Assertions.assertEquals(0.4, memosprite.getAttribute(AttributeType.DEFENCE_IGNORE).get(), EPS,
                "「召唤物…无视 40% 防御力」");
    }

    @Test
    public void nothingHappensWithoutTheEidolon() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(MEMOSPRITE_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();
        Assertions.assertEquals(0.0, ally.getAttribute(AttributeType.DEFENCE_IGNORE).get(), EPS,
                "before 星魂 1 the Skill grants no defence ignore");
    }

    // ==================================================================
    // 星魂 2: the first ultimate's skill points, and the Beatified's damage share
    // ==================================================================

    /**
     * 星魂 2's one sentence, read on the one Ultimate board it is paid on:「首次施放终结技后恢复 2 个战技点。【蒙福者】造成的
     * 伤害提高 30%」 - the points are a ONE-TIME payment (the marker is spent), and the share rides the state, once, from
     * rank 2 on.
     *
     * <p>⭐ One scenario: the pool and the share belong to the same cast ({@code sunday + 1204} at rank 2, his Ultimate),
     * and the rank levers ({@code shareAt}) are the same clause the sentence states; a case for each half would read
     * that cast twice.
     */
    @Test
    public void theFirstUltimatePaysTwoPointsAndTheBeatifiedSharesThirtyPercent() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL, false, null, null, 2);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        int pool = battle.getSkillPoints();

        battle.castImmediate(sunday.getSkills().get(SkillType.ULTRA), sunday, List.of(ally));
        battle.processRequests();
        int afterFirst = battle.getSkillPoints();

        double atTwo = shareAt(2);
        double atThree = shareAt(3);
        double atOne = shareAt(1);

        sunday.setCurrentEnergy(sunday.getMaxEnergy());
        battle.castImmediate(sunday.getSkills().get(SkillType.ULTRA), sunday, List.of(ally));
        battle.processRequests();
        int afterSecond = battle.getSkillPoints();

        Assertions.assertAll("星魂 2: 首次终结技的 2 点，与【char:1313/blessed_one】的 30%",
                () -> Assertions.assertEquals(pool + 2, afterFirst,
                        "[the points] 「首次施放终结技后恢复 2 个战技点」"),
                () -> Assertions.assertEquals(afterFirst, afterSecond,
                        "[once] …and the second Ultimate pays nothing: the marker was spent"),
                () -> Assertions.assertEquals(0.3, atTwo, EPS, "[the share] 「【char:1313/blessed_one】造成的伤害提高 30%」"),
                () -> Assertions.assertEquals(0.3, atThree, EPS,
                        "[no stack] …and it does not stack with itself when the Eidolon is higher"),
                () -> Assertions.assertEquals(0.0, atOne, "[before 星魂 2] before 星魂 2 there is no such share"));
    }

    // ==================================================================
    // 星魂 4: 8 energy at his own turn start
    // ==================================================================

    /** 星魂 4「回合开始时，恢复 8 点能量」: his own turns only. */
    @Test
    public void theFourthEidolonPaysEnergyOnHisTurn() {
        Assertions.assertEquals(8.0, energyAfterOneTurn(4), EPS, "星魂 4 pays 8 at his turn start");
        Assertions.assertEquals(0.0, energyAfterOneTurn(3), EPS, "and nothing before it");
    }

    // ==================================================================
    // 星魂 6: the stacking talent, and the overflow clause
    // ==================================================================

    /**
     * 星魂 6's talent sentence:「天赋的暴击率提高效果可以叠加，最多叠加 3 层，并使天赋的持续时间增加 1 回合」 - below rank 6
     * the talent REPLACES its modifier, from rank 6 it stacks to three, and the duration grows from 3 to 4 turns.
     *
     * <p>⭐ One scenario: the stack count and the duration are read off the same object (the CRIT_RATE modifier the
     * talent leaves on the ally in {@code scene(eidolon)}), so the cap and the extra turn are the same clause, not two.
     */
    @Test
    public void theTalentStacksToThreeAndLastsFourTurnsFromTheSixthEidolon() {
        long replacedAtFive = critRateStacks(5, 2);
        long stackedTwice = critRateStacks(6, 2);
        long fourCasts = critRateStacks(6, 4);
        long threeCasts = critRateStacks(6, 3);
        int durationAtFive = critRateDuration(5);
        int durationAtSix = critRateDuration(6);

        Assertions.assertAll("星魂 6: 叠加最多 3 层，持续 4 回合",
                () -> Assertions.assertEquals(1, replacedAtFive,
                        "[below 星魂 6] below 星魂 6 the second cast replaces the first"),
                () -> Assertions.assertEquals(2, stackedTwice, "[stacking] 「可以叠加」 — two casts, two layers"),
                () -> Assertions.assertEquals(3, fourCasts, "[the cap] 「最多叠加 3 层」"),
                () -> Assertions.assertEquals(3, threeCasts, "[at the cap] and three casts reach the cap exactly"),
                () -> Assertions.assertEquals(3, durationAtFive,
                        "[the duration before] before 星魂 6: 「持续 3 回合」"),
                () -> Assertions.assertEquals(4, durationAtSix,
                        "[the extra turn] 「并使天赋的持续时间增加 1 回合」"));
    }

    /**
     * The third sentence:「当目标暴击率超过 100%，每溢出 1% 暴击率提高 2% 暴击伤害」.
     *
     * <p>Measured with the ally crit rate forced over 100%: the CRIT DMG modifier must equal twice the overflow, and with the
     * rate below 100% it must be zero - which is the `max(..., 0)` in the expression doing its job.
     */
    @Test
    public void theOverflowCritRateBecomesCritDamage() {
        // The exact figure is computed inside the helper from the rate the ally actually holds (a snapshot), so these two
        // assertions only say WHICH WAY the clause goes.
        Assertions.assertTrue(overflowCritDamage(1.04) > 0, "over 100% the overflow becomes CRIT DMG");
        Assertions.assertEquals(0.0, overflowCritDamage(0.6), 1e-6, "below 100% nothing is converted");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** The damage share the Beatified ally carries after his Ultimate, at the given Eidolon rank. */
    private static double shareAt(int eidolon) {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL, false, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(sunday.getSkills().get(SkillType.ULTRA), sunday, List.of(ally));
        battle.processRequests();
        return ally.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StatModifierBuff.class).stream()
                .filter(buff -> buff.getAttribute() == AttributeType.ALL_DAMAGE_TYPE_BOOST)
                .mapToDouble(com.laosun.aluminium.buff.StatModifierBuff::getValue).sum();
    }

    private static double energyAfterOneTurn(int eidolon) {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL, false, null, null, eidolon);
        Battle battle = new Battle(List.of(sunday), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        // The trace pays 25 at start; the reading is the DIFFERENCE a turn makes. Announced directly: the action order
        // reaching his turn is its own job, and driving a whole round here would mix its outcome into the measurement.
        double before = sunday.getCurrentEnergy();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.TURN_START, sunday, null, 0, 0));
        battle.processRequests();
        return sunday.getCurrentEnergy() - before;
    }

    /** How many CRIT_RATE modifiers the ally carries after {@code casts} Skill casts, at that Eidolon rank. */
    private static long critRateStacks(int eidolon, int casts) {
        Battle battle = scene(eidolon);
        Character sunday = battle.characters().get(0);
        Character ally = battle.characters().get(1);
        for (int i = 0; i < casts; i++) {
            battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
            battle.processRequests();
        }
        return ally.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StatModifierBuff.class).stream()
                .filter(buff -> buff.getAttribute() == AttributeType.CRIT_CHANCE)
                .count();
    }

    /** The duration of the CRIT_RATE modifier after one Skill cast, at that Eidolon rank. */
    private static int critRateDuration(int eidolon) {
        Battle battle = scene(eidolon);
        Character sunday = battle.characters().get(0);
        Character ally = battle.characters().get(1);
        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();
        return ally.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StatModifierBuff.class).stream()
                .filter(buff -> buff.getAttribute() == AttributeType.CRIT_CHANCE)
                .mapToInt(com.laosun.aluminium.buff.AbstractBuff::duration)
                .max().orElse(0);
    }

    private static Battle scene(int eidolon) {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL, false, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    /** The CRIT DMG the talent adds when the ally crit rate is pushed to {@code critRate} first. */
    private static double overflowCritDamage(double critRate) {
        Battle battle = scene(6);
        Character sunday = battle.characters().get(0);
        Character ally = battle.characters().get(1);
        // "pure" is the value in the attribute's own units: a percent modifier is a share of the BASE and does not reliably
        // cross 100% (measured: the first version left the ally near 10%, and the clause correctly paid nothing).
        ally.getBuffManager().addBuff(com.laosun.aluminium.buff.StatModifierBuff.of(
                AttributeType.CRIT_CHANCE, "pure", critRate - ally.getAttribute(AttributeType.CRIT_CHANCE).get(),
                "buff", 3, false));
        battle.processRequests();
        double before = ally.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();
        // The clause is a SNAPSHOT of the crit rate at the moment the talent lands (which already includes the talent own
        // +20%), so the expectation is computed from the rate the ally actually holds afterwards.
        double critAfter = ally.getAttribute(AttributeType.CRIT_CHANCE).get();
        double expected = 2 * Math.max(0, critAfter - 1);
        double measured = ally.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
        Assertions.assertEquals(expected, measured, 1e-6, "2 x the overflow (" + critAfter + ")");
        return measured;
    }
}
