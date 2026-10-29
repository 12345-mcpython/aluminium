package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.GameData;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * The skill table is a data input now: a character built from an empty table must not resolve a real
 * skill row.
 */
public class SkillDataTest {

    /**
     * The explicit table is honoured by {@link SkillData}.
     */
    @Test
    public void theSkillTableCanBeReplaced() {
        Assertions.assertTrue(SkillData.init(1003, 1).isLoaded(), "the default table has 1003's basic attack");
        Assertions.assertFalse(SkillData.init(Map.of(), 1003, 1).isLoaded(),
                "the explicit empty table has no rows");
    }

    /**
     * A character built from an empty skill table must carry a placeholder, not the default row.
     */
    @Test
    public void anEmptySkillTableLeavesTheSlotUnloaded() {
        Character withSkills = CharacterFactory.create(1003, 80, true);
        Character withoutSkills = CharacterFactory.create(GameData.withSkills(Map.of()),
                1003, 80, true, null, null, 0);

        Skill with = withSkills.getSkills().get(SkillType.COMMON);
        Skill without = withoutSkills.getSkills().get(SkillType.COMMON);

        Assertions.assertNotNull(with, "the default character still has a COMMON slot");
        Assertions.assertNotNull(without, "the empty-table character still has a COMMON slot");
        Assertions.assertTrue(with.getData().isLoaded(), "the default slot resolves a real row");
        Assertions.assertFalse(without.getData().isLoaded(), "the empty table leaves the slot unloaded");
    }
}
