package com.laosun.aluminium.test.content.characters;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
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
 * 1214 Xueyi, from her own file (2026-09-29, round 196): the technique's 80%-ATK opening.
 *
 * <p>Same-pipeline reference again: the content's 0.8 against a hand-built 0.5 must be exactly 1.6.
 */
public class XueyiTest {
    private static final int XUEYI = 1214;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The ratio, the wiring and the control. */
    @Test
    public void theTechniqueDealsEightyPercentOfHerAttack() {
        double content = openingLoss(0);
        double reference = openingLoss(1);
        double undeclared = openingLoss(2);

        Assertions.assertEquals(0.0, undeclared, 1e-9, "「使用秘技后」 -- undeclared, so nothing");
        Assertions.assertTrue(reference > 0, "the reference must deal damage");
        Assertions.assertEquals(0.8 / 0.5, content / reference, 0.05,
                "content " + content + " vs reference " + reference + " (expected " + (0.8 / 0.5) + ")");
    }

    /** 0 = the shipped file, 1 = a hand-built 50% reference, 2 = the shipped file with no technique declared. */
    private static double openingLoss(int mode) {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL);
        if (mode == 1) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "DAMAGE");
            TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
            TriggerSpecs.set(effect, "percent", 0.5);
            TriggerSpecs.set(effect, "element", "Quantum");
            TriggerSpecs.set(effect, "target", "all_enemies");
            xueyi.setTriggerTable(new TriggerTable(XUEYI, List.of(TriggerSpecs.rule(
                    TriggerEvent.BATTLE_START.name(), List.of("self has_state 秘技"), effect))));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(xueyi), List.of(enemy), fixed());
        if (mode != 2) {
            battle.markTechniqueUsed(xueyi);
        }
        double before = enemy.getCurrentHp();
        battle.startBattle();
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
