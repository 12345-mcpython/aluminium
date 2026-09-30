package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** 1205's eidolon 2: 「刃处于【地狱变】状态时，暴击率提高 15%」. */
public class BladeEidolonStateTest {
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u5730\u72f1\u53d8";

    @Test
    public void theSecondEidolonRaisesCritRateOnlyWhileTheStateHolds() {
        double with = gain(true);
        double without = gain(false);
        System.out.println("[1205] withState=" + with + " withoutState=" + without);
        Assertions.assertEquals(0.15, with, 1e-6, "the eidolon row's own value");
        Assertions.assertEquals(0.0, without, 1e-9, "no state, no clause");
    }

    private static double gain(boolean state) {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        if (state) {
            unit.getBuffManager().addBuff(new StateBuff(STATE, 3));
        }
        double before = unit.getAttribute(AttributeType.CRIT_CHANCE).get();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        return unit.getAttribute(AttributeType.CRIT_CHANCE).get() - before;
    }
}
