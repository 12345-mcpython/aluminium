package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The battle's rosters are read-only views; {@code addCombatant} is the only admission path.
 */
public class BattleRosterTest {

    /**
     * A caller must not be able to empty a roster behind the battle's back.
     */
    @Test
    public void theRostersAreReadOnly() {
        Character character = CharacterFactory.create(1003, 80);
        Battle battle = new Battle(List.of(character), List.of(), new Random(0));

        Assertions.assertThrows(UnsupportedOperationException.class, () -> battle.characters().clear());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> battle.allies().clear());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> battle.enemies().clear());
    }
}
