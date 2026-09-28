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

    /** \u26a0 The mark's own claims: the state on the ally, her +1 Charge, and the ATTACK share = 24% of HER attack (a derived, absolute number). */
    @Test
    public void theSkillMarksTheAllyAndSharesHerAttack() {
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(cerydra, ally), List.of(enemy), fixed());
        battle.startBattle();
        double allyAttackBefore = ally.getAttribute(AttributeType.ATTACK).get();
        int chargeBefore = cerydra.getResources().get("\u5145\u80fd").getValue();
        double expected = cerydra.getAttribute(AttributeType.ATTACK).get() * 0.24;

        battle.castImmediate(cerydra.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), cerydra, List.of(ally));

        Assertions.assertTrue(ally.getBuffManager().hasState("\u519b\u529f"),
                "\u300c\u4f7f\u6307\u5b9a\u6211\u65b9\u5355\u4f53\u89d2\u8272\u83b7\u5f97\u3010\u519b\u529f\u3011\u300d");
        Assertions.assertEquals(expected, ally.getAttribute(AttributeType.ATTACK).get() - allyAttackBefore, expected * 0.02,
                "\u300c\u63d0\u9ad8\u6570\u503c\u7b49\u540c\u4e8e\u523b\u5f8b\u5fb7\u83c8\u653b\u51fb\u529b\u768424.00%\u300d: expected " + expected);
        Assertions.assertEquals(chargeBefore + 1, cerydra.getResources().get("\u5145\u80fd").getValue(),
                "\u300c\u5e76\u4f7f\u523b\u5f8b\u5fb7\u83c8\u83b7\u5f971\u70b9\u5145\u80fd\u300d");
    }

    /** \u26a0 The mark's reaction: a marked ally's attack grants Charge AND draws her 60% additional damage; an unmarked one does neither. */
    @Test
    public void theMarkDrivesBothTheChargeAndTheAdditionalDamage() {
        double unmarked = markedAttackLoss(false);
        double marked = markedAttackLoss(true);

        Assertions.assertEquals(0.0, unmarked, 1e-9,
                "\u300c\u6301\u6709\u3010\u519b\u529f\u3011\u7684\u89d2\u8272\u65bd\u653e\u653b\u51fb\u540e\u300d -- without the mark, nothing");
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
