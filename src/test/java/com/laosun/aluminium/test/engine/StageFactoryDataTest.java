package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.GameData;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.utils.StageFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * {@link StageFactory} passes the chosen {@link GameData} down to its wave spawns.
 */
public class StageFactoryDataTest {

    /**
     * An empty monster table must make the first wave fail, not fall back to {@code Constant}.
     */
    @Test
    public void aCustomPackWithNoMonstersFailsTheFirstWave() {
        GameData emptyMonsters = GameData.withMonsterConfigs(Map.of());

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> StageFactory.load(emptyMonsters, 103201),
                "the wave spawn must read the data pack it was given");

        Assertions.assertTrue(thrown.getMessage().contains("Unknown monster"),
                "and report the missing monster, not silently use Constant");
    }

    /**
     * The team assembly must read the same pack: an empty character table cannot build the roster.
     */
    @Test
    public void aCustomPackWithNoCharactersIsRefused() {
        GameData emptyCharacters = GameData.withCharacters(Map.of());

        Assertions.assertThrows(CharacterException.CharacterNotFoundException.class,
                () -> StageFactory.load(emptyCharacters, 103201),
                "the reference team must read the character table it was given");
    }

    /**
     * The default overload still assembles the stage.
     */
    @Test
    public void theDefaultOverloadStillBuilds() {
        Battle battle = StageFactory.load(GameData.DEFAULT, 103201);

        Assertions.assertFalse(battle.enemies.isEmpty(), "wave 1 is on the field");
        Assertions.assertFalse(battle.isOver(), "the battle is running");
    }
}
