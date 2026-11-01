package com.laosun.aluminium.test.content.characters.misha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1312 米沙's Talent, the segment half: 「我方全体每消耗 1 个战技点，增加 1 段米沙下一次终结技的攻击段数」.
 */
public class MishaSegmentTest {

    private static final int MISHA = 1312;
    private static final int LEVEL = 80;

    @Test
    public void spendingASkillPointAddsASegment() {
        Character misha = CharacterFactory.create(MISHA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(misha, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int before = misha.skillHitBonus(SkillType.ULTRA.ordinal() + 1);   // data slot 3 = the Ultimate
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, misha, misha, 1, 0));
        int after = misha.skillHitBonus(SkillType.ULTRA.ordinal() + 1);
        System.out.println("[1312] ult hits " + before + " -> " + after);
        Assertions.assertEquals(before + 1, after, "「增加 1 段」");
    }
}