package com.laosun.aluminium.test;

import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * P1-4 acceptance: the level travels from the builder / factory into the combatant,
 * and it stays mutable for scaling / test setups.
 */
public class LevelTest {
    @Test
    public void builderLevelReachesCombatant() {
        // 1409 = 风堇（已在 character_data.json）；Builder 的 weapon / relicSuit /
        // extraBasicPromote 都有非空默认值，所以只给 cid + level 就能 build。
        Character character = Character.builder().cid(1409).level(90).build();

        Assertions.assertEquals(90, character.getLevel());
    }

    @Test
    public void factoryDefaultsToEighty() {
        Assertions.assertEquals(80, Character.fromAttributes("x", 100, 100, 100, 100).getLevel());
        Assertions.assertEquals(80, Enemy.fromAttributes("e", 100, 100, 100, 100).getLevel());
    }

    @Test
    public void levelIsMutable() {
        Character character = Character.fromAttributes("x", 100, 100, 100, 100);

        character.setLevel(95);

        Assertions.assertEquals(95, character.getLevel());
    }
}
