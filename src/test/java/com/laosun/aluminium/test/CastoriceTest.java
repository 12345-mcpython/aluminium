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
 * 1407 Castorice, from her own file (2026-09-29, round 221): the talent's damage boost on HP loss — the HP_LOST event's first use here.
 */
public class CastoriceTest {
    private static final int CASTORICE = 1407;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 The boost needs an HP loss, and its three-stack cap is enforced by exceeding it. */
    @Test
    public void theBoostNeedsAnHpLossAndStopsAtThreeStacks() {
        Character castorice = CharacterFactory.create(CASTORICE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(castorice, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = castorice.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        // Only HP_LOST moves it: an unrelated event must not.
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        Assertions.assertEquals(0.0, castorice.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before, 1e-9,
                "\u300c\u6211\u65b9\u635f\u5931\u751f\u547d\u503c\u65f6\u300d -- an attack that costs no HP does nothing");

        battle.fireTriggers(TriggerEvent.HP_LOST, ally, ally, 0, 100);
        Assertions.assertEquals(0.2, castorice.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before, 1e-6,
                "\u300c\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad820%\u300d");

        for (int i = 0; i < 4; i++) {
            battle.fireTriggers(TriggerEvent.HP_LOST, ally, ally, 0, 100);
        }
        Assertions.assertEquals(0.6, castorice.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before, 1e-6,
                "\u300c\u6700\u591a\u53e0\u52a03\u5c42\u300d -- five firings must still read three stacks of 20%");
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
