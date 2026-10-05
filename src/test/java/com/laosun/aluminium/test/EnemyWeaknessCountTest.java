package com.laosun.aluminium.test;

import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * ⭐ CHARACTERISATION of {@code Enemy.weaknessCount()} (2026-09-30, first step of the cone-22004 diagnosis): the accessor must
 * agree with the data's own {@code stance_weak} list -- measured against 1002011, which lists Fire and Thunder.
 *
 * <p>★ Why this exists as its own step: cone 22004's per-weakness boost moved the damage by NOTHING (measured x1.0), and the
 * cheap way to find out why is to ask one question at a time. This is question one: does the count have a value at all?
 */
public class EnemyWeaknessCountTest {
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 90;

    @Test
    public void theCountAgreesWithTheData() {
        Enemy enemy = EnemyFactory.create(MONSTER, LEVEL, 1);
        int count = enemy.weaknessCount();
        System.out.println("[weakness] " + MONSTER + ": count=" + count
                + " weakToFire=" + enemy.isWeakTo(com.laosun.aluminium.enums.DamageElement.FIRE)
                + " weakToIce=" + enemy.isWeakTo(com.laosun.aluminium.enums.DamageElement.ICE));
        Assertions.assertEquals(2, count, "the data's stance_weak list for this monster");
        Assertions.assertTrue(count > 0, "and it is not empty -- the count is real");
        Assertions.assertTrue(enemy.isWeakTo(com.laosun.aluminium.enums.DamageElement.FIRE),
                "Fire is in the list the count is reading");
    }
}
