package com.laosun.aluminium.test.content.characters.sampo;

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
 * 1108 桑博's Eidolons 3 and 5: the two level clauses the data spells as {@code skill_add_level}.
 */
public class SampoEidolonTest {

    private static final int LEVEL = 80;
    private static final int SAMPO = 1108;

    @Test
    public void theLevelEidolonsRaiseTheirSlots() {
        Assertions.assertEquals(level(0, SkillType.SKILL) + 2, level(3, SkillType.SKILL), "E3: 战技 +2");
        Assertions.assertEquals(level(0, SkillType.COMMON) + 1, level(3, SkillType.COMMON), "E3: 普攻 +1");
        Assertions.assertEquals(level(0, SkillType.ULTRA) + 2, level(5, SkillType.ULTRA), "E5: 终结技 +2");
        Assertions.assertEquals(level(0, SkillType.TALENT) + 2, level(5, SkillType.TALENT), "E5: 天赋 +2");
    }

    private static int level(int eidolon, SkillType slot) {
        // Levels are raised by BATTLE_START rules, so the battle must have started before the slot reads its level.
        Character sampo = CharacterFactory.create(SAMPO, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(sampo)), new ArrayList<>(List.of(BattleFixture.monster())),
                BattleFixture.seeded());
        battle.startBattle();
        return 1 + sampo.skillLevelBonus(slot);
    }
}



