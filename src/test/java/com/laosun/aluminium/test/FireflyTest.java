package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1310 Firefly, from her own file (2026-09-29, round 216): the two Action Advances, as exact arithmetic on the queue's remaining time.
 *
 * <p>ADVANCE goes through `Queue.advanceActionByPercent`, documented as "skip `percent` of the REMAINING time". So 25% must leave 0.75 of the wait, and 100% must leave ~0.
 * The first draft watched `advanceRequests`, which is a different mechanism — it measured nothing and said so by returning 0.
 */
public class FireflyTest {
    private static final int FIREFLY = 1310;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ 25% of the REMAINING wait is skipped: what is left is 0.75 of what it was, and nothing moves without firing. */
    @Test
    public void theSkillLeavesThreeQuartersOfTheWait() {
        Character firefly = CharacterFactory.create(FIREFLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(firefly), List.of(enemy), fixed());
        battle.startBattle();
        double before = remaining(battle, firefly);
        Assertions.assertTrue(before > 0, "the fixture must have a wait to skip: " + before);

        battle.fireTriggers(TriggerEvent.SKILL_CAST, firefly, enemy, 0, 0);
        double after = remaining(battle, firefly);

        Assertions.assertEquals(0.75, after / before, 1e-6,
                "「使自身下一次行动提前25%」: " + before + " -> " + after);
    }

    /** ⚠ 100% means the wait is gone. */
    @Test
    public void theUltimateRemovesTheWaitEntirely() {
        Character firefly = CharacterFactory.create(FIREFLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(firefly), List.of(enemy), fixed());
        battle.startBattle();
        double before = remaining(battle, firefly);
        Assertions.assertTrue(before > 0, "the fixture must have a wait to skip: " + before);

        battle.fireTriggers(TriggerEvent.ULT_CAST, firefly, enemy, 0, 0);

        Assertions.assertEquals(0.0, remaining(battle, firefly), 1e-6,
                "「自身行动提前100%」: " + before + " -> 0");
    }

    /** The owner's remaining wait, read off the queue's public snapshot. */
    private static double remaining(Battle battle, Character unit) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == unit) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        throw new IllegalStateException("the unit is not in the action queue");
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
