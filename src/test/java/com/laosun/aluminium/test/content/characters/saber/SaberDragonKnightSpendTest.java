package com.laosun.aluminium.test.content.characters.saber;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1014 Saber's trace 龙之骑士, the conditional half:「若持有【炉心共鸣】且消耗后能回满能量，则消耗【魔力放出】为我方恢复 1 个战技点并使
 * Saber 立即行动」.
 *
 * <p>⚠ It must run on the SAME event as the Skill's own consuming branch and BEFORE it: a staircase probe showed
 * {@code self_stacks:炉心共鸣 >= 1} is false by the time {@code SKILL_CAST} fires (the points are already gone).
 */
public class SaberDragonKnightSpendTest {

    private static final int SABER = 1014;
    private static final int LEVEL = 80;
    private static final String RESONANCE = "炉心共鸣";
    private static final String MANA = "魔力放出";

    @Test
    public void enoughResonanceSpendsManaForAPointAndAnExtraTurn() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        saber.setCurrentEnergy(0);
        for (int i = 0; i < 45; i++) {
            saber.getBuffManager().addBuff(new StackBuff(RESONANCE, 1, true, 60));
        }
        Assertions.assertTrue(saber.getBuffManager().hasState(MANA), "precondition: 【魔力放出】 from entering battle");
        int pointsBefore = battle.getSkillPoints();
        Assertions.assertNull(battle.getExtraTurnActor(), "precondition: nothing scheduled");
        battle.castImmediate(saber.getSkills().get(SkillType.SKILL), saber, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        System.out.println("[1014] mana " + saber.getBuffManager().hasState(MANA)
                + " ; skill points " + pointsBefore + " -> " + battle.getSkillPoints()
                + " ; extra turn " + (battle.getExtraTurnActor() != null));
        Assertions.assertFalse(saber.getBuffManager().hasState(MANA), "「消耗【魔力放出】」");
        Assertions.assertEquals(pointsBefore + 1, battle.getSkillPoints(), "「为我方恢复 1 个战技点」");
        Assertions.assertSame(saber, battle.getExtraTurnActor(), "「使 Saber 立即行动」");
    }
}