package com.laosun.aluminium.test.content.characters;

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
 * 1315 Boothill, from his own file: the Standoff state on BOTH units, and the stack that both of its end conditions feed.
 */
public class BoothillTest {
    private static final int BOOTHILL = 1315;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The state lands on the enemy AND on him, and both stack rules read their own event. */
    @Test
    public void theStandoffLandsOnBothAndBothEndsFeedTheStack() {
        Character boothill = CharacterFactory.create(BOOTHILL, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(boothill), List.of(enemy), fixed());
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, boothill, enemy, 0, 0);
        Assertions.assertTrue(enemy.getBuffManager().hasState("绝命对峙"),
                "「使指定敌方单体及自身进入【绝命对峙】状态」 (puts a designated single enemy and herself into the Standoff state) -- the ENEMY");
        Assertions.assertTrue(boothill.getBuffManager().hasState("绝命对峙"),
                "and HIMSELF");

        Assertions.assertEquals(0, boothill.getBuffManager().stacksOf("优势口袋"), "nothing before either end condition");
        battle.fireTriggers(TriggerEvent.BREAK, boothill, enemy, 0, 0);
        Assertions.assertEquals(1, boothill.getBuffManager().stacksOf("优势口袋"),
                "「弱点被击破后…获得1层【优势口袋】」 (after the Weakness is Broken ... gains 1 stack of Pocket Trickshot (【优势口袋】))");
        battle.fireTriggers(TriggerEvent.KILL, boothill, enemy, 0, 0);
        Assertions.assertEquals(2, boothill.getBuffManager().stacksOf("优势口袋"),
                "「该目标被消灭…后…获得1层」 (after that target is killed ... gains 1 stack) -- the OTHER event feeds it too");

        for (int i = 0; i < 4; i++) {
            battle.fireTriggers(TriggerEvent.BREAK, boothill, enemy, 0, 0);
        }
        Assertions.assertEquals(3, boothill.getBuffManager().stacksOf("优势口袋"),
                "「最多叠加3层」 (stacks at most 3 times) -- seven firings must still read three");
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
