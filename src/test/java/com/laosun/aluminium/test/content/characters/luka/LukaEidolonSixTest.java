package com.laosun.aluminium.test.content.characters.luka;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1111 卢卡's Eidolon 6: 「本次强化普攻使用的【直冲拳】每施放过 1 段攻击，则额外使其当前承受的裂伤状态立即产生 1 次相当于原伤害
 * 8% 的伤害」.
 *
 * <p>The clause is per SEGMENT, which {@code DEALING_DAMAGE} already provides once per hit - no segment ordinal is needed.
 * The check plants a laceration on a target that survives, then compares his basic attack with and without the Eidolon.
 */
public class LukaEidolonSixTest {

    private static final int LUKA = 1111;
    private static final int LEVEL = 80;
    private static final String LACERATION = "裂伤";

    @Test
    public void eachSegmentTicksTheLacerationOnce() {
        double atFive = basicDamage(5, true);
        double atSix = basicDamage(6, true);
        double unbleeding = basicDamage(6, false);
        System.out.println("[1111] basic: E5 " + atFive + ", E6 " + atSix + " ; E6 on a healthy target " + unbleeding);
        Assertions.assertTrue(atSix > atFive, "「每施放过 1 段攻击…额外产生 1 次 8% 的伤害」");
        Assertions.assertEquals(atFive, unbleeding, atFive * 1e-6,
                "the control: with no laceration there is nothing to tick, at any rank");
    }

    /** His basic attack's damage against a target with (or without) a planted laceration. */
    private static double basicDamage(int eidolon, boolean bleeding) {
        Character luka = CharacterFactory.create(LUKA, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(luka)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (bleeding) {
            enemy.getBuffManager().addBuff(new DotBuff(luka, DamageElement.PHYSICAL, 10_000, 3, 0, LACERATION));
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(luka.getSkills().get(SkillType.COMMON), luka, List.of(enemy));
        battle.processRequests();
        Assertions.assertFalse(enemy.isDeath(), "the target must survive, or a second tick would settle nothing");
        return before - enemy.getCurrentHp();
    }
}