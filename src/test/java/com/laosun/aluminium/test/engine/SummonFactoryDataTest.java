package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.GameData;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.SummonFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * {@link SummonFactory} can read a chosen {@link GameData}, not only the static default.
 */
public class SummonFactoryDataTest {

    /**
     * An empty monster table must be honoured.
     */
    @Test
    public void aCustomPackWithNoMonstersIsRefused() {
        GameData emptyMonsters = GameData.withMonsterConfigs(Map.of());

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> SummonFactory.create(emptyMonsters, 1002040, 90, 1, Camp.ENEMY),
                "the factory must read the data pack it was given");

        Assertions.assertTrue(thrown.getMessage().contains("Unknown monster"),
                "and report the missing monster, not silently use Constant");
    }

    /**
     * The default overload still builds a real summon.
     */
    @Test
    public void theDefaultOverloadStillBuilds() {
        Summon summon = SummonFactory.create(GameData.DEFAULT, 1002040, 90, 1, Camp.ENEMY);

        Assertions.assertEquals(Camp.ENEMY, summon.getCamp());
        Assertions.assertTrue(summon.getMaxHp() > 0, "a real stat sheet");
    }
}
