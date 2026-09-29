package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StackBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A magnitude that scales with a counter: {@code scale: target_stacks:<NAME>} (and its {@code self_stacks:} twin).
 *
 * <p>Read at fire time from the <b>counter</b>, not from a field: the engine's counters are "several buffs with one name"
 * ({@code BuffManager.stacksOf}), so the test builds them directly -- three named stacks must make the value three times
 * the per-layer one, which is what separates a scale from a second copy of the first application.
 *
 * <p>⚠ The counter is built by the test on purpose. 1218's talent, whose ADD_STACK should have produced it, leaves no
 * stack at all (measured 2026-09-29: its DOT lands, stacksOf stays 0), so the per-layer clause that motivated this
 * capability is registered against that separate defect rather than shipped on top of it.
 */
public class StackScaledMagnitudeTest {
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;
    private static final String COUNTER = "\u6d4b\u8bd5\u5c42";

    @Test
    public void theMagnitudeIsThePerLayerValueTimesTheCount() {
        Assertions.assertEquals(0.03, landed(1), EPS, "one layer is the per-layer value itself");
        Assertions.assertEquals(0.09, landed(3), EPS,
                "three layers are three times it, not the same value applied three times");
    }

    /** Applies a counter-scaled 3%-per-layer reduction with the given number of stacks on the victim. */
    private static double landed(int stacks) {
        Character owner = CharacterFactory.create(ALLY, LEVEL);
        owner.setTriggerTable(new TriggerTable(9994, List.of(TriggerSpecs.rule(
                TriggerEvent.TURN_START.name(), List.of(), perLayer()))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        for (int i = 0; i < stacks; i++) {
            enemy.getBuffManager().addBuff(new StackBuff(COUNTER, 2, false, 5));
        }
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.TURN_START, owner, enemy, 0, 0);

        return enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
    }

    private static EffectSpec perLayer() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", AttributeType.RESISTANCE_REDUCTION.attributeString);
        TriggerSpecs.set(effect, "scale", "target_stacks:" + COUNTER);
        TriggerSpecs.set(effect, "percent", 0.03);
        TriggerSpecs.set(effect, "turns", 2);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }
}
