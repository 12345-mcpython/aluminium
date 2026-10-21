package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.GameData;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * {@link CharacterFactory} can read the character row from a chosen {@link GameData}.
 */
public class CharacterFactoryDataTest {

    /**
     * An empty character table must be honoured, not replaced by {@code Constant}.
     */
    @Test
    public void aCustomPackWithNoCharactersIsRefused() {
        GameData emptyCharacters = GameData.withCharacters(Map.of());

        Assertions.assertThrows(CharacterException.CharacterNotFoundException.class,
                () -> CharacterFactory.create(emptyCharacters, 1003, 80, false, null, null, 0),
                "the factory must read the character table it was given");
    }

    /**
     * The default overload still builds a real character.
     */
    @Test
    public void theDefaultOverloadStillBuilds() {
        Assertions.assertTrue(CharacterFactory.create(1003, 80).getMaxHp() > 0);
    }
}
