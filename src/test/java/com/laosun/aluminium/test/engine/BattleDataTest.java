package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.GameData;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * {@link Battle} reads the skills it attaches at battle start from its data pack.
 */
public class BattleDataTest {

    /**
     * The overworld skill slots attached by {@code startBattle} must use the battle's skill table.
     */
    @Test
    public void theBattleAttachesSkillsFromItsDataPack() {
        Character character = CharacterFactory.create(1003, 80, true);
        Battle battle = new Battle(List.of(character), List.of(), new Random(1),
                GameData.withSkills(Map.of()));

        battle.startBattle();

        Skill maze = character.getSkills().get(SkillType.MAZE);
        Assertions.assertNotNull(maze, "startBattle attaches the overworld basic attack");
        Assertions.assertFalse(maze.getData().isLoaded(),
                "the empty skill table must leave the attached slot unloaded");
    }
}
