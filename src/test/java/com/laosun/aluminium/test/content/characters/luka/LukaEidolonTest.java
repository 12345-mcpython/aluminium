package com.laosun.aluminium.test.content.characters.luka;

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
 * 1111 卢卡's Eidolons 3, 4 and 5: the two level bumps (data-driven, from {@code skill_add_level}) and 「每获得 1 层【斗志】，
 * 攻击力提高 5%，最多叠加 4 层」.
 */
public class LukaEidolonTest {

    private static final int LEVEL = 80;
    private static final int LUKA = 1111;

    @Test
    public void eidolonThreeAndFiveRaiseTheSkillLevels() {
        Battle e0 = battle(0);
        Battle e6 = battle(6);
        Assertions.assertEquals(level(e0, SkillType.SKILL) + 2, level(e6, SkillType.SKILL), "E3: 战技 +2");
        Assertions.assertEquals(level(e0, SkillType.TALENT) + 2, level(e6, SkillType.TALENT), "E3: 天赋 +2");
        Assertions.assertEquals(level(e0, SkillType.ULTRA) + 2, level(e6, SkillType.ULTRA), "E5: 终结技 +2");
        Assertions.assertEquals(level(e0, SkillType.COMMON) + 1, level(e6, SkillType.COMMON), "E5: 普攻 +1");
    }

    // ==================================================================

    private static Battle battle(int eidolon) {
        Character luka = CharacterFactory.create(LUKA, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(luka, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private static int level(Battle battle, SkillType slot) {
        return battle.characters().getFirst().skillLevelBonus(slot);
    }

    }




