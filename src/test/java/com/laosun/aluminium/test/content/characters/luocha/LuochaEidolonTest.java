package com.laosun.aluminium.test.content.characters.luocha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1203 罗刹's Eidolon 3 (the level bumps) and Eidolon 6 (「敌方全体全属性抗性降低 20%，持续 2 回合」).
 */
public class LuochaEidolonTest {

    private static final int LUOCHA = 1203;
    private static final int LEVEL = 80;

    @Test
    public void eidolonThreeRaisesSkillAndBasic() {
        Battle e0 = battle(0);
        Battle e3 = battle(3);
        Assertions.assertEquals(level(e0, SkillType.SKILL) + 2, level(e3, SkillType.SKILL), "战技 +2");
        Assertions.assertEquals(level(e0, SkillType.COMMON) + 1, level(e3, SkillType.COMMON), "普攻 +1");
    }

    @Test

    private static Battle battle(int eidolon) {
        Character luocha = CharacterFactory.create(LUOCHA, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(luocha, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    private static int level(Battle battle, SkillType slot) {
        return battle.characters().getFirst().skillLevelBonus(slot);
    }
}

