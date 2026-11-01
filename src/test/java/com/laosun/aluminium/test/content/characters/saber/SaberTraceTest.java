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

    /** 「施放战技时，使 Saber 的暴击伤害提高 50%，持续 2 回合」. */
    @Test
    public void starCrownRaisesCritDamageAfterASkill() {
        Character casting = CharacterFactory.create(SABER, LEVEL);
        Battle battle = battle(casting);
        double before = casting.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.castImmediate(casting.getSkills().get(SkillType.SKILL), casting, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        double castDelta = casting.getAttribute(AttributeType.CRIT_ATTACK).get() - before;

        Character control = CharacterFactory.create(SABER, LEVEL);
        double controlBefore = control.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle(control);
        for (int i = 0; i < 3; i++) {
            control.getBuffManager().addBuff(new com.laosun.aluminium.buff.StackBuff("炉心共鸣", 1, true, 60));
        }
        double controlDelta = control.getAttribute(AttributeType.CRIT_ATTACK).get() - controlBefore;
        System.out.println("[1014] crit damage: skill +" + castDelta + " ; the same points without a cast +" + controlDelta);
        Assertions.assertEquals(0.5, castDelta - controlDelta, 1e-9, "「暴击伤害提高 50%」, over and above the points");
    }

    private static Battle battle(Character saber) {
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }
    /**
     * 湖之祝福's opening clause:「战斗开始时若能量不足 60% 则使其恢复至 60%」.
     *
     * <p>Two directions: from empty it tops up to 60% of the cap; already above 60% it must do nothing.
     */
    @Test
    public void lakeBlessingTopsUpToSixtyPercent() {
        Character low = CharacterFactory.create(SABER, LEVEL);
        low.setCurrentEnergy(0);
        battle(low);
        double expected = 0.6 * low.getMaxEnergy();
        System.out.println("[1014] from 0 -> " + low.getCurrentEnergy() + " (cap " + low.getMaxEnergy() + ")");
        Assertions.assertEquals(expected, low.getCurrentEnergy(), 1e-9, "「恢复至 60%」");

        Character high = CharacterFactory.create(SABER, LEVEL);
        high.setCurrentEnergy(high.getMaxEnergy());
        battle(high);
        Assertions.assertEquals(high.getMaxEnergy(), high.getCurrentEnergy(), 1e-9,
                "「若能量不足 60%」: a full bar is left alone");
    }
}
