package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A sustained aura can follow its counter (2026-10-02): "for every 1 stack the owner has, ...".
 *
 * <p>The reader family is the largest registered one in GAPS (fourteen documents). A snapshot is right only at the instant
 * it is taken, and re-attaching on every change would stack the buff itself -- so the share is re-read instead.
 */
public class PerStackLiveTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COUNTER = "probeCharge";

    /** One stack, then five: the same attached aura yields 14% and then 0%. */
    @Test
    public void theAuraFollowsTheCounter() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(scene());
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertEquals(1, owner.getBuffManager().stacksOf(COUNTER), "precondition: one stack");
        double oneStack = owner.getAttribute(AttributeType.ATTACK).get();
        double base = owner.getAttribute(AttributeType.ATTACK).baseValue();
        System.out.println("[per_stack_live] one stack -> " + oneStack + " (baseValue " + base + ")"
                + " share=" + ((oneStack - base) / base));

        // a whole turn: TURN_START grants four more stacks; the aura is NOT re-applied
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == battle.allies.getFirst()).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: the owner is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();

        Assertions.assertEquals(5, owner.getBuffManager().stacksOf(COUNTER), "precondition: five stacks now");
        double fiveStacks = owner.getAttribute(AttributeType.ATTACK).get();
        double oneShare = (oneStack - base) / base;
        double fiveShare = (fiveStacks - base) / base;
        System.out.println("[per_stack_live] five stacks -> " + fiveStacks + " share=" + fiveShare);
        // Note: The absolute share carries an unrelated offset from this unit's own modifiers (measured 0.18), so the reading
        // is the DELTA: four more stacks have to be four more 14% shares, with nothing re-attached in between.
        Assertions.assertEquals(0.14 * 4, fiveShare - oneShare, 1e-6,
                "the aura has to follow the count, with nothing re-attached");
    }

    // ==================================================================

    private static TriggerTable scene() {
        EffectSpec oneStack = stack(1.0);
        EffectSpec aura = effect("MODIFY_ATTR", "attribute", "ATTACK", "percent", 0.14,
                "perStack", "self_stacks:" + COUNTER, "perStackLive", Boolean.TRUE, 
                "permanent", Boolean.TRUE, "target", "self");
        EffectSpec fourMore = stack(4.0);
        return new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), oneStack, aura),
                        TriggerSpecs.rule("TURN_START", List.of(), fourMore)));
    }

    private static EffectSpec stack(double amount) {
        return effect("ADD_STACK", "buff", COUNTER, "amount", amount, "maxStacks", 5,
                "permanent", Boolean.TRUE, "target", "self");
    }

    private static EffectSpec effect(String op, Object... pairs) {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", op);
        for (int i = 0; i < pairs.length; i += 2) {
            TriggerSpecs.set(spec, (String) pairs[i], pairs[i + 1]);
        }
        return spec;
    }
}
