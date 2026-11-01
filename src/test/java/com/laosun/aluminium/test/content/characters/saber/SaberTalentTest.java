package com.laosun.aluminium.test.content.characters.saber;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
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
 * 1014 Saber's talent 龙之炉心, the two halves its file states: 「进入战斗时获得 1 点【炉心共鸣】」 and 「我方任意目标施放终结技时，
 * Saber 造成的伤害提高 60%，持续 2 回合，并获得 3 点【炉心共鸣】」.
 */
public class SaberTalentTest {

    private static final int SABER = 1014;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;

    @Test
    public void enteringBattleGrantsOneResonance() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(saber, CharacterFactory.create(ALLY, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Assertions.assertTrue(saber.getBuffManager().hasState("炉心共鸣"), "「进入战斗时，获得 1 点【炉心共鸣】」");
        System.out.println("[1014] resonance at start: " + saber.getBuffManager().stacksOf("炉心共鸣"));
        Assertions.assertEquals(1, saber.getBuffManager().stacksOf("炉心共鸣"));
    }

    @Test
    public void anAllysUltimateRaisesHerDamageAndPaysThreeResonance() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(saber, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int before = saber.getBuffManager().stacksOf("炉心共鸣");
        double boostBefore = saber.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, ally, ally, 0, 0));
        System.out.println("[1014] resonance " + before + " -> " + saber.getBuffManager().stacksOf("炉心共鸣")
                + " ; boost " + boostBefore + " -> " + saber.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
        Assertions.assertEquals(before + 3, saber.getBuffManager().stacksOf("炉心共鸣"),
                "「并获得 3 点【炉心共鸣】」");
        Assertions.assertTrue(saber.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() > boostBefore,
                "「Saber 造成的伤害提高 60%，持续 2 回合」");
    }
}