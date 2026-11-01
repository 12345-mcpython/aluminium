package com.laosun.aluminium.test.content.characters.arlan;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** 1008 阿兰 战技:「消耗等同于阿兰生命上限 15% 的生命值」. */
public class ArlanSkillCostTest {

    @Test
    public void theSkillPaysFifteenPercentOfMaxHp() {
        Character him = CharacterFactory.create(1008, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(him)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = him.getCurrentHp();
        double expected = him.getMaxHp() * 0.15;
        battle.castImmediate(him.getSkills().get(SkillType.SKILL), him, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        double lost = before - him.getCurrentHp();
        System.out.println("[1008] skill cost " + lost + " ; expected " + expected);
        Assertions.assertEquals(expected, lost, expected * 0.01, "「生命上限 15%」");
    }
}