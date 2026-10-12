package com.laosun.aluminium.test.content.characters.aventurinewaveflair;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1513 (砂金•戏浪)'s talent milestone: 「【热意】达到10点时，砂金•戏浪会立即施放1次<b>固定计入20笑点</b>的【举杯！敬炽烈一夏】，
 * 并在施放后使自身下一次在阿哈时刻中施放的欢愉技强化为【All in！敬炽烈一夏】」.
 *
 * <p><b>What this replaced.</b> The file shipped the clause as an approximation, with an empty {@code source}: the cast fired
 * on <b>TURN_START</b> with a level test, so 「达到 10 点」 was read as 「回合开始时够 10 点」 - a wrong trigger, which is the
 * legal-but-wrong trap this project refuses. Two neighbours were wrong in the same area: 「每<b>消耗</b>1点【热意】…造成 1 次
 * 21%」 sat on {@code RESOURCE_CHANGED} with {@code times_from: event_amount}, so a <b>gain</b> of four points dealt four
 * hits; and the spend that the same sentence states had no {@code actor == self}, so <b>anybody's</b> Elation cast drained
 * her counter. All three are pinned below, each with the reading that the old shape would have produced.
 *
 * <p><b>The new lifetime.</b> 「下一次在阿哈时刻中施放的欢愉技」 is a slot swap that must end on the <b>Elation</b> cast and
 * on nothing else: {@code next_skill} would end it on a 战技 and {@code cast_end} inside the cast that granted it. So the
 * swap rides {@code until: next_elation_skill} (the engine judge for the lifetime itself is {@code BuffLifetimeTest}).
 */
public class AventurineWaveflairMilestoneTest {
    private static final int OWNER = 1513;
    private static final int ALLY = 1505;              // an Elation ally: their cast must not touch HER counter
    private static final String FERVOR = "热意";
    private static final int THRESHOLD = 10;
    private static final int FIXED_LAUGHS = 20;

    private Character wolf;
    private Character ally;
    private Enemy enemy;
    private Battle battle;

    /**
     * The crossing casts, spends the whole counter and installs the All-in swap - and the NEXT Elation cast consumes it.
     *
     * <p>The crossing is reached by the talent's own +1 (the ally-attack path), so the damage read here is the auto-cast's
     * alone: a 战技 would deal its own 240% in the same breath.
     */
    @Test
    public void theCrossingCastsSpendsAndInstallsTheSwap() {
        build();
        wolf.getResources().gain(FERVOR, THRESHOLD - 1);
        Assertions.assertEquals(9, fervor(), "precondition: one point short of the threshold");

        int frozen = battle.countedLaughPoints();
        double before = enemy.getCurrentHp();
        alleyAttacks();
        double cast = before - enemy.getCurrentHp();
        System.out.println("[1513] the crossing: counter " + fervor() + ", the commanded cast dealt " + cast
                + " (fixed " + FIXED_LAUGHS + "), the Elation slot now holds " + slotOf(wolf));
        Assertions.assertTrue(cast > 0, "「立即施放1次…欢愉技」: the crossing performs a cast");
        Assertions.assertEquals(0, fervor(), "and that cast spends ALL of it, exactly as the skill's own sentence says");
        Assertions.assertEquals(21, slotOf(wolf),
                "「下一次…强化为【All in！敬炽烈一夏】」 is installed in the Elation slot (data row 21)");

        // ⚠ The fixed 20 is pinned STRUCTURALLY here, and numerically by the ENGINE judge. Measured while writing this:
        // a cross-cast damage ratio cannot pin it in this fixture, because the content's own Aha Moment reward moves the
        // laugh snapshot between the two casts (the crossing's cast read 554.77, her own next one 1829.52) - the ratio
        // would measure that difference, not the field. What this layer can honestly assert is that the rule STATES 20.
        Assertions.assertEquals(FIXED_LAUGHS, fixedLaughsOf(wolf),
                "「固定计入 20 笑点」 rides the commanded cast (its number is pinned by CastCountedLaughsTest)");
        Assertions.assertTrue(frozen >= 0, "the snapshot the other cast folds is a different count: " + frozen);

        double beforeOwn = enemy.getCurrentHp();
        battle.castImmediate(wolf.getSkills().get(SkillType.ELATION_SKILL), wolf, List.of(enemy));
        battle.processRequests();
        System.out.println("[1513] after her own Elation cast the slot holds " + slotOf(wolf) + " and it dealt "
                + (beforeOwn - enemy.getCurrentHp()) + " against the crossing's " + cast);
        Assertions.assertEquals(20, slotOf(wolf),
                "next_elation_skill: the swap ends on the Elation cast it was waiting for");
    }

    /**
     * ⚠ The trigger is the CROSSING, not a turn start. The shape this replaced would cast at the beginning of her turn
     * whenever the counter happened to be high enough - and would leave the counter standing.
     */
    @Test
    public void aTurnStartIsNotTheCrossing() {
        build();
        wolf.getResources().gain(FERVOR, 12);
        double before = enemy.getCurrentHp();

        battle.fireTriggers(TriggerEvent.TURN_START, wolf, wolf, 0, 0);
        battle.processRequests();

        System.out.println("[1513] a turn start with 12 points: damage " + (before - enemy.getCurrentHp())
                + ", counter " + fervor());
        Assertions.assertEquals(0, before - enemy.getCurrentHp(), 1e-9,
                "「达到 10 点」 is a crossing: a turn beginning is not one");
        Assertions.assertEquals(12, fervor(), "and nothing spent the counter");
    }

    /**
     * ⚠ 「每<b>消耗</b>1点…造成 1 次 21%」 - a GAIN deals nothing. The shape this replaced hung on RESOURCE_CHANGED with
     * {@code times_from: event_amount}, so the two points gained here would have dealt two hits.
     */
    @Test
    public void aGainBelowTheThresholdDealsNothing() {
        build();
        double before = enemy.getCurrentHp();
        alleyAttacks();
        alleyAttacks();

        System.out.println("[1513] two gained points below the threshold: damage " + (before - enemy.getCurrentHp())
                + ", counter " + fervor());
        Assertions.assertEquals(2, fervor(), "precondition: two points were gained");
        Assertions.assertEquals(0, before - enemy.getCurrentHp(), 1e-9,
                "nothing was CONSUMED, so the per-point extra damage must not happen");
    }

    /**
     * ⚠ The spend belongs to HER cast: somebody else's Elation cast leaves her counter alone.
     *
     * <p>⚠ The counter starts BELOW the threshold on purpose. Measured while writing this: an ally's Elation cast also
     * fires {@code ALLY_ATTACK}, which her talent answers with +1 热意 - so starting at 12 crosses the threshold and her
     * own auto-cast fires and spends it, correctly. Starting at 5 isolates the gate: the ally's cast may add its one
     * point (5 to 6) and must do nothing else. Had the merged rule fired on somebody else's cast, it would have spent
     * all six and dealt six extra hits.
     */
    @Test
    public void someoneElsesElationCastDoesNotSpendHerFervor() {
        build();
        wolf.getResources().gain(FERVOR, 5);
        double beforeEnemy = enemy.getCurrentHp();

        battle.castImmediate(ally.getSkills().get(SkillType.ELATION_SKILL), ally, List.of(enemy));
        battle.processRequests();

        System.out.println("[1513] after the ALLY's Elation cast: her counter " + fervor() + ", the ally's damage "
                + (beforeEnemy - enemy.getCurrentHp()));
        Assertions.assertTrue(beforeEnemy - enemy.getCurrentHp() > 0, "precondition: the ally's cast really settled");
        Assertions.assertEquals(6, fervor(),
                "one point from the ally's attack, and nothing spent: 「施放时会消耗所有【热意】」 is about HER cast");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private void build() {
        wolf = CharacterFactory.create(OWNER, 80);
        // A crit roll would make every damage ratio below a coin toss, so it is pinned off.
        wolf.setAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_CHANCE,
                new com.laosun.aluminium.models.DoubleValue(0));
        ally = CharacterFactory.create(ALLY, 80);
        enemy = BattleFixture.monster();
        battle = new Battle(List.of(wolf, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
    }

    /**
     * One ally attack, as the event her talent answers: 「队友施放攻击后，砂金•戏浪获得1点【热意】」.
     *
     * <p>⚠ The event is fired directly rather than by an attack, so the reading is the talent's own point and not the
     * ally's swing: what is under test here is the counter and what the crossing does with it.
     */
    private void alleyAttacks() {
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0);
        battle.processRequests();
    }

    private int fervor() {
        return wolf.getResources().value(FERVOR);
    }

    /**
     * The laugh count the shipped rule states on its commanded cast, read off the compiled table.
     */
    private static Integer fixedLaughsOf(Character unit) {
        return unit.getTriggerTable().rulesFor(TriggerEvent.RESOURCE_CHANGED).stream()
                .filter(rule -> rule.id().equals("talent_autocasts_elation"))
                .flatMap(rule -> rule.effects().stream())
                .filter(effect -> "CAST_SKILL".equals(effect.getOp()))
                .map(effect -> effect.getCastCountedLaughs())
                .findFirst().orElse(null);
    }

    /**
     * The data row her Elation slot currently holds: 20 = 【举杯！敬炽烈一夏】, 21 = 【All in！敬炽烈一夏】.
     */
    private static int slotOf(Character unit) {
        return unit.getSkills().get(SkillType.ELATION_SKILL).getSkillSlot();
    }
}
