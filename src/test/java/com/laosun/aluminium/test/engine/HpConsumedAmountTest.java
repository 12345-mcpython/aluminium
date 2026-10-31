package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
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
 * {@code HP_CONSUMED} carries the HP the effect actually paid in its <b>amount</b> slot.
 *
 * <p><b>Why this file exists.</b> The amount used to be passed as the HIT COUNT:
 * {@code Simple.of(HP_CONSUMED, spender, spender, (int) round(spent), 0)}. Every rule reading {@code event_amount} on
 * this event therefore saw 0 - measured as 23039's {@code event_amount > 500} never firing even when more than 500 HP
 * was paid. A hit count is "how many targets the attack connected with", which this event does not have.
 *
 * <p>The fixtures use HP shares of the carrier's maximum (the op's own spelling): 60% of 1409's bar is above 500, 30%
 * is below it, so one character states both sides of the threshold.
 *
 * <p>The second rule watches the OLD slot ({@code hit_count > 500}): it must stay silent, or the swap was undone.
 */
public class HpConsumedAmountTest {

    private static final int OWNER = 1409;
    private static final int LEVEL = 80;

    /**
     * Paying more than 500 HP fires the amount condition, and the hit-count slot stays empty.
     */
    @Test
    public void moreThanFiveHundredHpIsReadAsTheAmountNotTheHitCount() {
        Fixture f = new Fixture(0.60);
        double before = f.owner.getCurrentHp();
        f.fire();

        Assertions.assertTrue(before - f.owner.getCurrentHp() > 500,
                "fixture premise: the price must exceed the threshold (paid " + (before - f.owner.getCurrentHp()) + ")");
        Assertions.assertEquals(1, f.owner.getBuffManager().stacksOf("超额标记"),
                "event_amount > 500 fires when the paid HP exceeds 500");
        Assertions.assertEquals(0, f.owner.getBuffManager().stacksOf("旧槽标记"),
                "the paid amount must NOT ride the hit-count slot any more");
    }

    /**
     * Paying less than 500 HP leaves both rules silent.
     */
    @Test
    public void lessThanFiveHundredHpIsBelowTheThreshold() {
        Fixture f = new Fixture(0.30);
        f.fire();

        Assertions.assertEquals(0, f.owner.getBuffManager().stacksOf("超额标记"),
                "the condition is a threshold, not an existence check");
        Assertions.assertEquals(0, f.owner.getBuffManager().stacksOf("旧槽标记"));
    }

    // ==================================================================

    private static final class Fixture {
        private final Character owner;
        private final Battle battle;

        private Fixture(double percentOfMaxHp) {
            owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
            EffectSpec pay = new EffectSpec();
            TriggerSpecs.set(pay, "op", "CONSUME_HP");
            TriggerSpecs.set(pay, "scale", "owner_max_hp");
            TriggerSpecs.set(pay, "percent", percentOfMaxHp);
            TriggerSpecs.set(pay, "target", "self");

            TriggerSpec over = TriggerSpecs.rule("HP_CONSUMED", List.of("event_amount > 500"), marker("超额标记"));
            TriggerSpecs.set(over, "id", "over_500");
            TriggerSpec oldSlot = TriggerSpecs.rule("HP_CONSUMED", List.of("hit_count > 500"), marker("旧槽标记"));
            TriggerSpecs.set(oldSlot, "id", "old_slot");

            owner.setTriggerTable(new TriggerTable(OWNER,
                    List.of(TriggerSpecs.rule("BASIC_ATTACK", List.of("actor == self"), pay), over, oldSlot)));
            battle = new Battle(List.of(owner, CharacterFactory.create(1001, LEVEL)),
                    List.of(BattleFixture.monster()), BattleFixture.seeded());
            battle.startBattle();
        }

        private void fire() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, owner, battle.enemies().getFirst(), 1, 0));
        }

        private static EffectSpec marker(String name) {
            EffectSpec stack = new EffectSpec();
            TriggerSpecs.set(stack, "op", "ADD_STACK");
            TriggerSpecs.set(stack, "buff", name);
            TriggerSpecs.set(stack, "amount", 1.0);
            TriggerSpecs.set(stack, "maxStacks", 5);
            TriggerSpecs.set(stack, "permanent", Boolean.TRUE);
            TriggerSpecs.set(stack, "target", "self");
            return stack;
        }
    }
}

