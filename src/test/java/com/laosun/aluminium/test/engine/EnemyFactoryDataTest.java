package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.GameData;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * {@link EnemyFactory} can read a chosen {@link GameData}, not only the static default.
 */
public class EnemyFactoryDataTest {

    /**
     * An empty monster table must be honoured: falling back to {@code Constant} would build the enemy.
     */
    @Test
    public void aCustomPackWithNoMonstersIsRefused() {
        GameData emptyMonsters = GameData.withMonsterConfigs(Map.of());

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> EnemyFactory.create(emptyMonsters, 1002011, 90, 1),
                "the factory must read the data pack it was given");

        Assertions.assertTrue(thrown.getMessage().contains("Unknown monster"),
                "and report the missing monster, not silently use Constant");
    }

    /**
     * The default overload still builds a real enemy.
     */
    @Test
    public void theDefaultOverloadStillBuilds() {
        Enemy enemy = EnemyFactory.create(GameData.DEFAULT, 1002011, 90, 1);

        Assertions.assertEquals(90, enemy.getLevel());
        Assertions.assertTrue(enemy.getMaxHp() > 0, "a real stat sheet");
    }
}
