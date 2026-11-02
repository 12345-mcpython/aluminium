package com.laosun.aluminium.test.content.characters.sampo;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1108 桑博's technique:「进入战斗时有 100% 固定概率使敌方每个单体目标行动延后 25%」.
 *
 * <p>The judge reads the action bar's remaining wait, which is what 行动延后 shortens/extends - not a buff count.
 */
public class SampoTechniqueTest {

    @Test
    public void theTechniqueDelaysEveryEnemy() {
        double with = wait(true);
        double without = wait(false);
        System.out.println("[1108] enemy wait: with technique " + with + " ; without " + without);
        Assertions.assertTrue(with > without, "「行动延后 25%」 must lengthen the wait");
        // 「行动延后 25%」 pushes the bar by 25% of the target's PERIOD (10000 / speed), not by 25% of its remaining wait -
        // measured: a period of 75.76 gets exactly 18.94 added, while the wait itself went 113.64 -> 132.58.
        double period = 10000.0 / BattleFixture.monster().getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        Assertions.assertEquals(0.25 * period, with - without, 0.01, "25% of the action period");
    }

    private static double wait(boolean technique) {
        Character him = CharacterFactory.create(1108, 80);
        if (technique) {
            him.getBuffManager().addBuff(new StateBuff("秘技", 1));
        }
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(him)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // The action bar owns the wait, so the judge reads its own snapshot ({@code remainingWaitOf} in the Hyacine tests).
        for (var signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == enemy) {
                return battle.queue().getTimeRemaining(signal);
            }
        }
        throw new AssertionError("the enemy is not on the action bar");
    }
}