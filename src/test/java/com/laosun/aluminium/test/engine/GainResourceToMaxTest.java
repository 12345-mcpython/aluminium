package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code GAIN_RESOURCE} with {@code to_max} raises a counter to its declared maximum, and does NOT spill into overflow.
 *
 * <p>Reader: 1503 Pearl (真珠)'s 充能 - 「【深度学习】具有 3 点充能」. The counter IS the state's duration, so re-entering the
 * state must restore the FULL three rather than add three to whatever is left; with one charge still up, "+3" would give
 * four and the state would outlast the document by a use.
 *
 * <p>⚠ <b>Why this is an ENGINE case rather than a Pearl case.</b> On Pearl's own counter the field changes nothing
 * observable: a resource with no overflow allowance clamps at its maximum, so {@code amount: 3} against a cap of 3 already
 * lands on three from anywhere. Measured - the first version of her case was a mutation SURVIVOR for exactly that reason.
 * The field earns its place where the counter CAN hold more than its maximum, which is what an overflow allowance is: there
 * a plain gain pours the spare three on top, and only {@code to_max} stops at the ceiling.
 *
 * <p>Two-sided: the same grant with the field lands exactly on the maximum, and without it lands above.
 */
public class GainResourceToMaxTest {
    private static final int PEARL = 1503;
    private static final String COUNTER = "溢出的计数";

    /**
     * With the field, the counter stops at its maximum however much is granted.
     */
    @Test
    public void toMaxStopsAtTheDeclaredMaximum() {
        int reached = grant(1, 3, true, 5);
        System.out.println("[to_max] from 1, granting 3 with to_max and 5 of overflow headroom: " + reached);
        Assertions.assertEquals(3, reached, "the maximum, not the maximum plus the spare");
    }

    /**
     * Without it, the same grant spills into the overflow allowance - which is the difference the field makes.
     */
    @Test
    public void aPlainGainSpillsIntoOverflow() {
        int reached = grant(1, 3, false, 5);
        System.out.println("[to_max] from 1, granting 3 WITHOUT to_max: " + reached);
        Assertions.assertEquals(4, reached, "1 + 3, allowed past the ceiling by the overflow allowance");
    }

    /**
     * And a counter that is already full is a no-op, not a no-op that fills the overflow.
     */
    @Test
    public void atTheMaximumItChangesNothing() {
        int reached = grant(3, 3, true, 5);
        System.out.println("[to_max] from the maximum, granting 3 with to_max: " + reached);
        Assertions.assertEquals(3, reached, "already full: nothing to raise");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Grants from a counter declared with the given maximum and overflow allowance, and returns where it ends up.
     *
     * @param from     the counter's starting value
     * @param amount   what the rule grants
     * @param toMax    whether the rule states {@code to_max}
     * @param overflow the overflow allowance the declaration carries (the ceiling is maximum + this)
     */
    private static int grant(int from, int amount, boolean toMax, int overflow) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Battle battle = new Battle(List.of(pearl), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character unit = battle.characters().getFirst();

        // The counter is the fixture's own, declared with an overflow allowance - the case is about the FIELD, so the
        // counter is built to have one. `setMaxOverflow` is the raw write the declaration path uses.
        unit.getResources().register(new com.laosun.aluminium.models.Resource(COUNTER, 3, from));
        unit.getResources().get(COUNTER).setMaxOverflow(overflow);

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(effect, "resource", COUNTER);
        TriggerSpecs.set(effect, "amount", (double) amount);
        if (toMax) {
            TriggerSpecs.set(effect, "toMax", true);
        }
        unit.setTriggerTable(new TriggerTable(PEARL, List.of(
                TriggerSpecs.rule("TURN_START", List.of("actor == self"), effect))));
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, unit, unit, 0, 0));
        battle.processRequests();
        return unit.getResources().value(COUNTER);
    }
}
