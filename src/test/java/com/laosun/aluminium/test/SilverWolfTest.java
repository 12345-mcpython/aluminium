package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1006 Silver Wolf, from her own file (2026-09-29, round 223): the Ultimate's defence shred, measured as a fraction of the enemy's own defence.
 */
public class SilverWolfTest {
    private static final int SILVER_WOLF = 1006;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 Exactly 45% of the enemy's own defence, and nothing at all until the Ultimate is cast. */
    @Test
    public void theUltimateShredsFortyFivePercentOfTheDefence() {
        Character silverWolf = CharacterFactory.create(SILVER_WOLF, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(silverWolf), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertTrue(before > 0, "the fixture must have defence to shred: " + before);

        // A control: firing an unrelated event must not shred anything.
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, silverWolf, enemy, 0, 0);
        Assertions.assertEquals(0.0, (before - enemy.getAttribute(AttributeType.DEFENCE).get()) / before, 1e-9,
                "an unrelated event must not shred defence");

        battle.fireTriggers(TriggerEvent.ULT_CAST, silverWolf, enemy, 0, 0);
        double after = enemy.getAttribute(AttributeType.DEFENCE).get();

        Assertions.assertEquals(0.45, (before - after) / before, 1e-6,
                "\u300c\u4f7f\u6307\u5b9a\u654c\u65b9\u5355\u4f53\u9632\u5fa1\u529b\u964d\u4f4e45.00%\u300d of its own defence: " + before + " -> " + after);
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
