package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * 1315 Boothill, from his own file (2026-09-29, round 226): the Standoff state on BOTH units, and the stack that both of its end conditions feed.
 */
public class BoothillTest {
    private static final int BOOTHILL = 1315;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 The state lands on the enemy AND on him, and both stack rules read their own event. */
    @Test
    public void theStandoffLandsOnBothAndBothEndsFeedTheStack() {
        Character boothill = CharacterFactory.create(BOOTHILL, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(boothill), List.of(enemy), fixed());
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, boothill, enemy, 0, 0);
        Assertions.assertTrue(enemy.getBuffManager().hasState("\u7edd\u547d\u5bf9\u5cd9"),
                "\u300c\u4f7f\u6307\u5b9a\u654c\u65b9\u5355\u4f53\u53ca\u81ea\u8eab\u8fdb\u5165\u3010\u7edd\u547d\u5bf9\u5cd9\u3011\u72b6\u6001\u300d -- the ENEMY");
        Assertions.assertTrue(boothill.getBuffManager().hasState("\u7edd\u547d\u5bf9\u5cd9"),
                "and HIMSELF");

        Assertions.assertEquals(0, boothill.getBuffManager().stacksOf("\u4f18\u52bf\u53e3\u888b"), "nothing before either end condition");
        battle.fireTriggers(TriggerEvent.BREAK, boothill, enemy, 0, 0);
        Assertions.assertEquals(1, boothill.getBuffManager().stacksOf("\u4f18\u52bf\u53e3\u888b"),
                "\u300c\u5f31\u70b9\u88ab\u51fb\u7834\u540e\u2026\u83b7\u5f971\u5c42\u3010\u4f18\u52bf\u53e3\u888b\u3011\u300d");
        battle.fireTriggers(TriggerEvent.KILL, boothill, enemy, 0, 0);
        Assertions.assertEquals(2, boothill.getBuffManager().stacksOf("\u4f18\u52bf\u53e3\u888b"),
                "\u300c\u8be5\u76ee\u6807\u88ab\u6d88\u706d\u2026\u540e\u2026\u83b7\u5f971\u5c42\u300d -- the OTHER event feeds it too");

        for (int i = 0; i < 4; i++) {
            battle.fireTriggers(TriggerEvent.BREAK, boothill, enemy, 0, 0);
        }
        Assertions.assertEquals(3, boothill.getBuffManager().stacksOf("\u4f18\u52bf\u53e3\u888b"),
                "\u300c\u6700\u591a\u53e0\u52a03\u5c42\u300d -- seven firings must still read three");
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
