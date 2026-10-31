package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.equipment.Weapon;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 「持续 3 回合」 must be three turns.
 *
 * <p><b>The clock, and why the data says four.</b> A timed buff spends one turn at each boundary of its carrier's own turn. A
 * buff granted <b>before</b> that carrier's first turn - which is what 「战斗开始时」 is - meets the ceremony that opens turn 1
 * before it has been live for any turn at all, so it used to pay only turns 1-2. Measured (2026-10-19 audits of 锋镝 and
 * 幽邃) and now compensated in the data with {@code turns: 4}, so the sentence's three turns are the three this pins.
 */
public class ConeDurationClockTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;

    private Battle battle;
    private Character wearer;
    private Enemy enemy;

    private void build(int cone) {
        wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, 1));
        enemy = BattleFixture.monster();
        battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();
    }

    /**
     * 锋镝 (20000): the crit rate is there for three of her turns and gone on the fourth.
     */
    @Test
    public void theConeKeepsItsCritRateForThreeTurns() {
        build(20000);
        double boosted = wearer.getAttribute(AttributeType.CRIT_CHANCE).get();
        Assertions.assertTrue(boosted > 0.05, "precondition: the cone granted its crit rate at battle start");

        for (int turn = 1; turn <= 3; turn++) {
            fullTurnOf(wearer);
            Assertions.assertEquals(boosted, wearer.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9,
                    "turn " + turn + " still has it: 「持续 3 回合」 counts this one");
        }
        fullTurnOf(wearer);
        Assertions.assertTrue(wearer.getAttribute(AttributeType.CRIT_CHANCE).get() < boosted,
                "the fourth turn does not - three turns is three turns");
    }

    /**
     * 幽邃 (20004): the same clock, read through its own attribute.
     */
    @Test
    public void theOtherBattleStartConeRunsOnTheSameClock() {
        build(20004);
        double boosted = wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get();
        Assertions.assertTrue(boosted > 0.05, "precondition: the cone granted its effect hit rate at battle start");

        for (int turn = 1; turn <= 3; turn++) {
            fullTurnOf(wearer);
            Assertions.assertEquals(boosted, wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get(), 1e-9,
                    "turn " + turn + " still has it");
        }
        fullTurnOf(wearer);
        Assertions.assertTrue(wearer.getAttribute(AttributeType.EFFECT_HIT_RATE).get() < boosted,
                "the fourth turn does not");
    }

    /**
     * One of the wearer's own turns: the boundary that opens it and the one that closes it.
     */
    private void fullTurnOf(Character unit) {
        battle.setCurrentMove(battle.queue().snapshot().stream()
                .filter(signal -> signal.getCanHit() == unit)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no signal for that unit")));
        battle.beforeMove();
        battle.afterMove();
    }

}
