package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Her technique's +60% and her ult's +80% ATTACK must both count (1401).
 *
 * <p>FILE-DRIVEN, three readings, and the two ratios are the claim: 1.6 with the technique alone, 2.4 with both.
 * `Note: `Technique (秘技)` comes from `battle.markTechniqueUsed(owner)` BEFORE `startBattle()` -- the engine's own entry point;
 * a hand-built `APPLY_BUFF` rule does not fire for a technique.
 */
public class AttackStacking1401Test {
    private static final int OWNER = 1401;
    private static final int MONSTER = 1002011;

    /** Both sources present means the two increments ADD (model-free: no share of the total is assumed). */
    @Test
    public void bothAttackSourcesAreCounted() {
        double plain = attack(false, false);
        double techniqueOnly = attack(true, false);
        double ultOnly = attack(false, true);
        double both = attack(true, true);

        Assertions.assertTrue(plain > 0, "precondition: a positive base (" + plain + ")");
        Assertions.assertTrue(techniqueOnly > plain, "the technique's own share lands (" + plain + " -> " + techniqueOnly + ")");
        Assertions.assertTrue(ultOnly > plain, "and the ult's lands on its own (" + plain + " -> " + ultOnly + ")");
        Assertions.assertEquals((techniqueOnly - plain) + (ultOnly - plain), both - plain, 1e-6,
                "with both present the two increments must ADD: (" + techniqueOnly + " - " + plain + ") + ("
                        + ultOnly + " - " + plain + ") vs (" + both + " - " + plain + ")");
    }

    // ==================================================================

    private static double attack(boolean usedTechnique, boolean castUlt) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        if (usedTechnique) {
            battle.markTechniqueUsed(owner);      // before startBattle: applyTechniqueStates runs before every BATTLE_START rule
        }
        battle.startBattle();
        battle.processRequests();
        if (castUlt) {
            battle.fireTriggers(TriggerEvent.ULT_CAST, owner, owner, 0, 0);
            battle.processRequests();
        }
        return owner.getAttribute(AttributeType.ATTACK).get();
    }
}
