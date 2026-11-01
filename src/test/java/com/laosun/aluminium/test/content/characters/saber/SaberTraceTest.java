package com.laosun.aluminium.test.content.characters.saber;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1014 Saber's three traces, the halves that are stateable: 龙之骑士's +20% CRIT with 【魔力放出】 on entry, and 星之冠冕's
 * +50% CRIT DAMAGE for 2 turns after a Skill.
 */
public class SaberTraceTest {

    private static final int SABER = 1014;
    private static final int LEVEL = 80;

    @Test
    public void dragonKnightRaisesCritAndGrantsManaRelease() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        battle(saber);
        System.out.println("[1014] crit " + saber.getAttribute(AttributeType.CRIT_CHANCE).get()
                + " ; mana " + saber.getBuffManager().hasState("魔力放出"));
        Assertions.assertTrue(saber.getAttribute(AttributeType.CRIT_CHANCE).get() >= 0.2, "「暴击率提高 20%」");
        Assertions.assertTrue(saber.getBuffManager().hasState("魔力放出"), "「进入战斗…获得【魔力放出】」");
    }

    @Test
    public void starCrownRaisesCritDamageAfterASkill() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        Battle battle = battle(saber);
        double before = saber.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.castImmediate(saber.getSkills().get(SkillType.SKILL), saber, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        double after = saber.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[1014] crit damage " + before + " -> " + after);
        Assertions.assertEquals(0.5, after - before, 1e-9, "「暴击伤害提高 50%」");
    }

    private static Battle battle(Character saber) {
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }
}