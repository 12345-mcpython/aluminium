package com.laosun.aluminium.test.trigger.damage;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code times_from: "steps:<counter>/<N>"}: the repeat count is how many WHOLE groups of N the counter holds,
 * {@code floor(layers / N)}.
 *
 * <p>Written for 1504 Ashveil (不死途)'s Ultimate:「拥有【婪酣】时，**每消耗 4 层**可额外造成 1 次…」. The probe counts settled instances
 * instead of comparing damage, so the arithmetic is what is being measured and nothing else.
 */
public class TimesFromStepsTest {
    private static final int CARRIER = 1204;
    private static final int LEVEL = 80;

    @Test
    public void theRepeatCountIsTheWholeGroups() {
        Assertions.assertEquals(0, hitsWith(3), "three layers are not a group");
        Assertions.assertEquals(1, hitsWith(4), "four layers are one group");
        Assertions.assertEquals(2, hitsWith(8), "eight layers are two groups");
        Assertions.assertEquals(2, hitsWith(9), "…and nine round down to two");
    }

    /** How many instances the grouped DAMAGE settles with {@code layers} of the counter. */
    private static int hitsWith(int layers) {
        Character carrier = CharacterFactory.create(CARRIER, LEVEL);
        carrier.setTriggerTable(table());
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(10_000);
        Battle battle = new Battle(List.of(carrier), List.of(enemy), BattleFixture.alwaysRoll(0.99));
        battle.startBattle();
        battle.processRequests();
        for (int i = 0; i < layers; i++) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TOUGHNESS_REDUCED, carrier, enemy, 1, 0));
            battle.processRequests();
        }
        Assertions.assertEquals(layers, carrier.getBuffManager().stacksOf("层"), "precondition: the counter is set up");
        int before = carrier.getBuffManager().stacksOf("命中");
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, carrier, enemy, 1, 0));
        battle.processRequests();
        return carrier.getBuffManager().stacksOf("命中") - before;
    }

    /** Two rules: grow the counter on a toughness hit, and settle the grouped damage on the Ultimate. */
    private static TriggerTable table() {
        EffectSpec stack = new EffectSpec();
        TriggerSpecs.set(stack, "op", "ADD_STACK");
        TriggerSpecs.set(stack, "buff", "层");
        TriggerSpecs.set(stack, "amount", 1.0);
        TriggerSpecs.set(stack, "maxStacks", 99);
        TriggerSpecs.set(stack, "permanent", true);
        TriggerSpecs.set(stack, "target", "self");

        EffectSpec count = new EffectSpec();
        TriggerSpecs.set(count, "op", "ADD_STACK");
        TriggerSpecs.set(count, "buff", "命中");
        TriggerSpecs.set(count, "amount", 1.0);
        TriggerSpecs.set(count, "maxStacks", 99);
        TriggerSpecs.set(count, "permanent", true);
        TriggerSpecs.set(count, "target", "self");

        EffectSpec damage = new EffectSpec();
        TriggerSpecs.set(damage, "op", "DAMAGE");
        TriggerSpecs.set(damage, "element", "Fire");
        TriggerSpecs.set(damage, "target", "target");
        TriggerSpecs.set(damage, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(damage, "percent", 0.01);
        TriggerSpecs.set(damage, "timesFrom", "steps:self_stacks:层/4");

        return new TriggerTable(9931, List.of(
                TriggerSpecs.rule(TriggerEvent.TOUGHNESS_REDUCED.name(), null, stack),
                TriggerSpecs.rule(TriggerEvent.ULT_CAST.name(), null, damage),
                TriggerSpecs.rule(TriggerEvent.DEALING_DAMAGE.name(), null, count)));
    }
}
