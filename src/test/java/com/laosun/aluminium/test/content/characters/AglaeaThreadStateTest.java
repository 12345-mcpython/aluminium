package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1402 Aglaea (阿格莱雅)'s "when casting an attack, make the target enter the [间隙织线] state", judged both ways with "while the Garmentmaker (衣匠) is on the field" as the variable.
 *
 * <p>Note: The state is applied by a rule and read back through `hasState`, which is also what the follow-up damage's condition asks --
 * the two halves of the sentence compose, and this pins the applying half.
 */
public class AglaeaThreadStateTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String STATE = "间隙织线";

    @Test
    public void withTheMemoSpriteOutTheAttackThreadsTheTarget() {
        Assertions.assertTrue(attacked(true), "the Garmentmaker (衣匠) is out, so the attack marks the target");
    }

    @Test
    public void withoutItTheTargetIsUntouched() {
        Assertions.assertFalse(attacked(false), "no Garmentmaker (衣匠), no mark -- the sentence is conditional on it");
    }

    /** Whether her attack left the state on the target, with or without the Garmentmaker (衣匠) on the field. */
    private static boolean attacked(boolean withMemoSprite) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(List.of(aglaea, ally), List.of(enemy), noCrit);
        battle.startBattle();
        if (withMemoSprite) {
            battle.summonMemosprite(aglaea);
        }
        // Note: COMMON: her Skill heals/summons, so her attack is the basic one.
        battle.castImmediate(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(enemy));
        return enemy.getBuffManager().hasState(STATE);
    }
}
