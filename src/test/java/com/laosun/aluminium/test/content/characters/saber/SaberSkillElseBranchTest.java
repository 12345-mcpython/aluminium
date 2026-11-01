package com.laosun.aluminium.test.content.characters.saber;

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
 * 1014 Saber's Skill, the <b>else</b> branch: 「否则立即获得 3 点【炉心共鸣】」.
 *
 * <p>「否则」 is the negation of the fill condition, written as the flipped comparison (numeric conditions may not use the
 * {@code !} prefix - that would turn an unreadable NaN into a pass). At one stack the bar cannot be filled, so this branch
 * is the one that runs: the cast hands her 3 points.
 */
public class SaberSkillElseBranchTest {

    private static final int SABER = 1014;
    private static final int LEVEL = 80;
    private static final String RESONANCE = "炉心共鸣";

    @Test
    public void aCastThatCannotFillTheBarPaysThreePoints() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int before = saber.getBuffManager().stacksOf(RESONANCE);
        Assertions.assertTrue(8.0 * before < saber.getMaxEnergy(), "precondition: this cast cannot fill the bar");
        battle.castImmediate(saber.getSkills().get(SkillType.SKILL), saber, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        System.out.println("[1014] resonance " + before + " -> " + saber.getBuffManager().stacksOf(RESONANCE));
        Assertions.assertEquals(before + 3, saber.getBuffManager().stacksOf(RESONANCE),
                "「否则立即获得 3 点【炉心共鸣】」");
    }
}