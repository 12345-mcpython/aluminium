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
 * 1014 Saber's Skill, the branch that only opens when the stacks can fill her bar:「若持有【炉心共鸣】且消耗后<b>能回满能量</b>，则
 * 每点使本次战技倍率提高 14%，并在攻击后消耗所有【炉心共鸣】为 Saber 恢复能量」.
 *
 * <p>A counter is SEVERAL buffs with one name, so 45 points means 45 additions - a single {@code StackBuff(name, 44, ...)}
 * carries one instance and made an earlier probe look like a dead rule.
 */
public class SaberSkillResonanceTest {

    private static final int SABER = 1014;
    private static final int LEVEL = 80;
    private static final String RESONANCE = "炉心共鸣";

    @Test
    public void enoughResonancePaysEightPerPointAndEmptiesTheCounter() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        saber.setCurrentEnergy(0);
        for (int i = 0; i < 45; i++) {
            saber.getBuffManager().addBuff(new StackBuff(RESONANCE, 1, true, 60));
        }
        int points = saber.getBuffManager().stacksOf(RESONANCE);
        Assertions.assertTrue(8.0 * points >= saber.getMaxEnergy(),
                "precondition: the points can fill the bar (" + points + " x 8 vs " + saber.getMaxEnergy() + ")");
        double energyBefore = saber.getCurrentEnergy();
        battle.castImmediate(saber.getSkills().get(SkillType.SKILL), saber, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        System.out.println("[1014] points " + points + " ; energy " + energyBefore + " -> " + saber.getCurrentEnergy()
                + " ; points after " + saber.getBuffManager().stacksOf(RESONANCE));
        // The refund is capped by the bar: 46 points x 8 = 368 on a 360-energy Ultimate fills it, it does not overflow.
        Assertions.assertEquals(Math.min(8.0 * points, saber.getMaxEnergy()), saber.getCurrentEnergy() - energyBefore, 1e-9,
                "「每消耗 1 点【炉心共鸣】固定恢复 8 点能量」, capped by the bar");
        Assertions.assertEquals(0, saber.getBuffManager().stacksOf(RESONANCE), "「消耗所有【炉心共鸣】」");
    }
}