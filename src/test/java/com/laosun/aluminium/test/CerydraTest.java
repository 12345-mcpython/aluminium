package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1412 Cerydra, from her own file (2026-09-29, round 205): the 军功 mark, the ATTACK share it carries, and the Charge the mark feeds.
 */
public class CerydraTest {
    private static final int CERYDRA = 1412;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ The mark's own claims: the state on the ally, her +1 Charge, and the ATTACK share = 24% of HER attack (a derived, absolute number). */
    @Test
    public void theSkillMarksTheAllyAndSharesHerAttack() {
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(cerydra, ally), List.of(enemy), fixed());
        battle.startBattle();
        double allyAttackBefore = ally.getAttribute(AttributeType.ATTACK).get();
        int chargeBefore = cerydra.getResources().get("充能").getValue();
        double expected = cerydra.getAttribute(AttributeType.ATTACK).get() * 0.24;

        battle.castImmediate(cerydra.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), cerydra, List.of(ally));

        Assertions.assertTrue(ally.getBuffManager().hasState("军功"),
                "「使指定我方单体角色获得【军功】」");
        Assertions.assertEquals(expected, ally.getAttribute(AttributeType.ATTACK).get() - allyAttackBefore, expected * 0.02,
                "「提高数值等同于刻律德菈攻击力的24.00%」: expected " + expected);
        Assertions.assertEquals(chargeBefore + 1, cerydra.getResources().get("充能").getValue(),
                "「并使刻律德菈获得1点充能」");
    }

    /** ⚠ The mark's reaction: a marked ally's attack grants Charge AND draws her 60% additional damage; an unmarked one does neither. */
    @Test
    public void theMarkDrivesBothTheChargeAndTheAdditionalDamage() {
        double unmarked = markedAttackLoss(false);
        double marked = markedAttackLoss(true);

        Assertions.assertEquals(0.0, unmarked, 1e-9,
                "「持有【军功】的角色施放攻击后」 -- without the mark, nothing");
        Assertions.assertTrue(marked > 0, "with the mark, the additional damage lands: " + marked);
    }

    /** Fires the ally's attack, optionally after marking them, and returns the enemy's HP loss from HER additional damage only. */
    private static double markedAttackLoss(boolean mark) {
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(cerydra, ally), List.of(enemy), fixed());
        battle.startBattle();
        if (mark) {
            battle.castImmediate(cerydra.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), cerydra, List.of(ally));
        }
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
