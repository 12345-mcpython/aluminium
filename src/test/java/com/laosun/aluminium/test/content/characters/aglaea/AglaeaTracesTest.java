package com.laosun.aluminium.test.content.characters.aglaea;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1402 阿格莱雅's traces, as far as the document's sentences can be written today.
 */
public class AglaeaTracesTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;

    /**
     * 行迹 飞驰之阳:「战斗开始时，若自身能量不足 50%，恢复自身能量至 50%」.
     *
     * <p>Measured as a floor, not a gain: she starts at zero, the trace lifts her to exactly half her maximum (350 ⇒
     * 175), and it must not overshoot - "恢复至" is a target, not an amount added.
     */
    @Test
    public void theRacingSunFillsTheEnergyBarToHalf() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        double beforeBattle = aglaea.getCurrentEnergy();
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        double half = aglaea.getMaxEnergy() * 0.5;
        Assertions.assertTrue(half > 0, "precondition: she has an energy bar");
        Assertions.assertTrue(beforeBattle < half, "precondition: she starts below the floor (" + beforeBattle + ")");
        Assertions.assertEquals(half, aglaea.getCurrentEnergy(), 1e-9,
                "「恢复自身能量至 50%」: " + half + " of " + aglaea.getMaxEnergy());
    }

    /**
     * 行迹 短视之惩:「处于【至高之姿】状态时，阿格莱雅与衣匠的攻击力提高，提高数值等同于阿格莱雅速度的 720% + 衣匠速度的
     * 360%」.
     *
     * <p>Measured as the difference the trace makes, on both units: the expected raise is computed from the two speeds the
     * battle actually holds (hers and the memosprite's), so the two shares cannot be swapped without failing.
     */
    @Test
    public void theTraceRaisesBothAttackValues() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double herAttackBefore = aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();

        battle.castImmediate(aglaea.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                aglaea, List.of(enemy));
        battle.processRequests();
        com.laosun.aluminium.entity.Summon garmentmaker = battle.summonOf(aglaea);
        Assertions.assertNotNull(garmentmaker, "precondition: the Ultimate brought the memosprite out");

        double herSpeed = aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        double itsSpeed = garmentmaker.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        double raise = 7.2 * herSpeed + 3.6 * itsSpeed;
        Assertions.assertTrue(raise > 0, "precondition: both speeds are real numbers");

        Assertions.assertEquals(herAttackBefore + raise,
                aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get(), 1e-6,
                "「阿格莱雅与衣匠的攻击力提高…」 — hers: 720% of her speed + 360% of the memosprite's");
        Assertions.assertEquals(herAttackBefore + raise,
                garmentmaker.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get(), 1e-6,
                "…and the memosprite gets the same number (its base ATTACK is hers, see the panel)");
    }
}
