package com.laosun.aluminium.test.content.characters.misha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1312 米沙's Ultimate: 「每段攻击前有 20% 的基础概率使目标陷入冻结状态」.
 *
 * <p>The clause is per SEGMENT, which is why it hangs on {@code DEALING_DAMAGE} (fired once per hit, before that hit is
 * settled) instead of on the cast. The check forces the roll to succeed and looks at the enemy's state.
 */
public class MishaFreezeTest {

    private static final int MISHA = 1312;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    @Test
    public void eachSegmentRollsTheFreeze() {
        Assertions.assertTrue(frozenAfterUltimate(0.0), "with the roll forced to succeed the enemy is frozen");
        Assertions.assertFalse(frozenAfterUltimate(0.99), "with the roll forced to fail it is not");
    }

    private static boolean frozenAfterUltimate(double roll) {
        Character misha = CharacterFactory.create(MISHA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        // ⚠ Not BattleFixture.monster(): that enemy carries a 冻结 debuff resist of 1.0 (see GepardFreezeTest), so a
        // freeze never lands on it. The attribute dummy has no such resist.
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(List.of(misha, ally), List.of(enemy), BattleFixture.alwaysRoll(roll));
        battle.startBattle();
        battle.castImmediate(misha.getSkills().get(SkillType.ULTRA), misha, List.of(enemy));
        battle.processRequests();
        boolean frozen = enemy.getBuffManager().hasState("冻结");
        System.out.println("[1312] roll " + roll + " -> frozen=" + frozen);
        return frozen;
    }
}