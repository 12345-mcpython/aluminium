package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The level Eidolons (3 and 5) of two characters that had none of them written: 1213 and 1221.
 */
public class LevelEidolonsTest {

    private static final int LEVEL = 80;

    @Test
    public void danHengImbibitorLunae() {
        assertLevels(1213, SkillType.SKILL, 2);
        assertLevels(1213, SkillType.COMMON, 1);
        assertLevels(1213, SkillType.ULTRA, 2);
        assertLevels(1213, SkillType.TALENT, 2);
    }

    @Test
    public void yunli() {
        assertLevels(1221, SkillType.ULTRA, 2);
        assertLevels(1221, SkillType.COMMON, 1);
        assertLevels(1221, SkillType.SKILL, 2);
        assertLevels(1221, SkillType.TALENT, 2);
    }

    private static void assertLevels(int cid, SkillType slot, int expected) {
        Assertions.assertEquals(expected, level(cid, 6, slot) - level(cid, 0, slot),
                cid + " " + slot + " should rise by " + expected);
    }

    private static int level(int cid, int eidolon, SkillType slot) {
        Character unit = CharacterFactory.create(cid, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(unit, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return unit.skillLevelBonus(slot);
    }
}