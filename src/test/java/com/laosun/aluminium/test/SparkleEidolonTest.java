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
 * 1306 Sparkle (花火)'s eidolon 2 (before enhancement): "each layer of the talent additionally makes our targets ignore 8% of the target's defence when dealing damage".
 *
 * <p>The layers are the talent's own, which is why that modifier had to be named first; the value is read with the counter
 * scale, and DEFENCE_IGNORE is a ratio attribute, so that is the right shape. Three ways: rank 2 states 8% per layer, the
 * count tracks the events, and rank 0 states nothing (the gate is half the clause).
 */
public class SparkleEidolonTest {
    private static final int SPARKLE = 1306;
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;
    private static final String COUNTER = "叙述性诡计";

    @Test
    public void rankTwoTurnsEachTalentLayerIntoEightPercentDefenceIgnore() {
        Assertions.assertEquals(0.08, ignoreAfter(2, 1), EPS, "one layer is 8%");
        Assertions.assertEquals(0.24, ignoreAfter(2, 3), EPS, "three layers, the talent's cap, are 24%");
        Assertions.assertEquals(0.0, ignoreAfter(0, 3), EPS, "eidolon 2 is the gate: without it nothing is stated");
    }

    private static double ignoreAfter(int rank, int layers) {
        Character sparkle = CharacterFactory.create(SPARKLE, LEVEL, true, null, null, rank);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(sparkle, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        for (int i = 0; i < layers; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, sparkle, enemy, 1, 1);
        }
        Assertions.assertEquals(layers, sparkle.getBuffManager().stacksOf(COUNTER),
                "precondition: the talent counted " + layers + " layer(s)");
        return ally.getAttribute(AttributeType.DEFENCE_IGNORE).get();
    }
}
