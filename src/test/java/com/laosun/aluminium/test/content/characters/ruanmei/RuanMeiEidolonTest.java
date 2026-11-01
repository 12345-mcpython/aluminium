package com.laosun.aluminium.test.content.characters.ruanmei;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1303 阮•梅's Eidolons 3, 4 and 5.
 */
public class RuanMeiEidolonTest {

    private static final int RUANMEI = 1303;
    private static final int LEVEL = 80;

    @Test
    public void eidolonFourRaisesHerBreakEffectOnABreak() {
        Character ruanmei = CharacterFactory.create(RUANMEI, LEVEL, true, null, null, 4);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(ruanmei, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = ruanmei.getAttribute(AttributeType.BREAKING_EFFECT).get();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.BREAK, ruanmei, enemy, 0, 0));
        double after = ruanmei.getAttribute(AttributeType.BREAKING_EFFECT).get();
        System.out.println("[1303] break effect " + before + " -> " + after);
        Assertions.assertEquals(1.0, after - before, 1e-9, "「击破特攻提高 100%」");
    }

    @Test
    public void eidolonsThreeAndFiveRaiseTheLevels() {
        Battle e0 = battle(0);
        Battle e6 = battle(6);
        Assertions.assertEquals(level(e0, SkillType.ULTRA) + 2, level(e6, SkillType.ULTRA), "E3: 终结技 +2");
        Assertions.assertEquals(level(e0, SkillType.TALENT) + 2, level(e6, SkillType.TALENT), "E3: 天赋 +2");
        Assertions.assertEquals(level(e0, SkillType.SKILL) + 2, level(e6, SkillType.SKILL), "E5: 战技 +2");
        Assertions.assertEquals(level(e0, SkillType.COMMON) + 1, level(e6, SkillType.COMMON), "E5: 普攻 +1");
    }

    private static Battle battle(int eidolon) {
        Character ruanmei = CharacterFactory.create(RUANMEI, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(ruanmei, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    private static int level(Battle battle, SkillType slot) {
        return battle.characters().getFirst().skillLevelBonus(slot);
    }
}