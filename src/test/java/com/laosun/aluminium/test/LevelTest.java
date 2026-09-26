package com.laosun.aluminium.test;

import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * P1-4 acceptance: the level travels from the builder / factory into the combatant,
 * and it stays mutable for scaling / test setups.
 */
public class LevelTest {
    @Test
    public void builderLevelReachesCombatant() {
        // 1409 = Hyacine (already in character_data.json); the Builder's weapon / relicSuit /
        // extraBasicPromote all have non-empty defaults, so giving only cid + level is enough to build.
        Character character = Character.builder().cid(1409).level(90).build();

        Assertions.assertEquals(90, character.getLevel());
    }

    @Test
    public void factoryDefaultsToEightyAndLevelIsMutable() {
        Character character = Character.fromAttributes("x", 100, 100, 100, 100);

        Assertions.assertEquals(80, character.getLevel());
        Assertions.assertEquals(80, Enemy.fromAttributes("e", 100, 100, 100, 100).getLevel());

        character.setLevel(95);

        Assertions.assertEquals(95, character.getLevel());
    }
}
