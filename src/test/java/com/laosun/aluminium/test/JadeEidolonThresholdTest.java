package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1314 Jade (翡翠)'s 星魂 (Eidolon) 2: "when [当品] stacks to 15 layers, Jade's CRIT Rate is increased by 18%" -- a threshold on the counter she now ships.
 *
 * <p>Two ways, and the difference is the whole clause: with the technique her battle-start total is sixteen, so the
 * threshold holds; without it she has one layer, so nothing is stated.
 */
public class JadeEidolonThresholdTest {
    private static final int JADE = 1314;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;
    private static final String COUNTER = "当品";

    @Test
    public void sixteenLayersPassTheThresholdAndOneDoesNot() {
        double withTechnique = critChance(true);
        double without = critChance(false);
        // Note: A difference, not an absolute: the unit carries the game's inherent +0.05 crit chance, so `get() - baseValue()`
        // mixes the two (measured: 0.23 where the clause owns 0.18).
        Assertions.assertEquals(0.18, withTechnique - without, EPS,
                "【当品】叠加至15层时，暴击率提高18%");
        // The absence of the clause still reads the game's inherent 5% crit chance, so the control is 0.05.
        Assertions.assertEquals(0.05, without, EPS, "one layer is below the threshold, so only the inherent remains");
    }

    private static double critChance(boolean technique) {
        Character jade = CharacterFactory.create(JADE, LEVEL, true, null, null, 2);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(jade), List.of(enemy), new Random(0));
        if (technique) {
            battle.markTechniqueUsed(jade);
        }
        battle.startBattle();
        Assertions.assertTrue(jade.getBuffManager().stacksOf(COUNTER) >= (technique ? 15 : 1),
                "precondition: the counter reached " + jade.getBuffManager().stacksOf(COUNTER));
        return jade.getAttribute(AttributeType.CRIT_CHANCE).get()
                - jade.getAttribute(AttributeType.CRIT_CHANCE).baseValue();
    }
}
