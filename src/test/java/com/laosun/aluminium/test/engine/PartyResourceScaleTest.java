package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
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
 * A magnitude may read a battle-level PARTY counter (2026-10-02).
 *
 * <p>The reader is 1513's reward: it must hand [好活当赏] the number of [笑点] the Aha moment spent, and [笑点] is declared
 * `scope: PARTY` -- the battle owns it, so `self_stacks:` (which reads a unit) cannot reach it.
 */
public class PartyResourceScaleTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COUNTER = "probeParty";

    /** Seven on the counter, and 0.01 x = 0.0added when the rule fires. */
    @Test
    public void theMagnitudeComesFromThePartyCounter() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(scene());
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertEquals(7, battle.partyResourceValue(COUNTER), "precondition: seven on the counter");
        double before = owner.getAttribute(AttributeType.ATTACK).get();

        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == battle.allies.getFirst()).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: the owner is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();

        double after = owner.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[party-scale] counter=" + battle.partyResourceValue(COUNTER)
                + " attack " + before + " -> " + after);
        Assertions.assertEquals(0.07, after - before, 1e-9,
                "0.01 x the party counter's 7 -- the counter is what the magnitude read");
    }

    // ==================================================================

    private static TriggerTable scene() {
        EffectSpec grant = effect("GAIN_RESOURCE", "resource", COUNTER, "amount", 7.0);
        EffectSpec derived = effect("MODIFY_ATTR", "attribute", "ATTACK",
                "scale", "party_resource:" + COUNTER, "percent", 0.01,
                "permanent", Boolean.TRUE, "target", "self");
        return new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), grant),
                        TriggerSpecs.rule("TURN_START", List.of(), derived)),
                List.of(new ResourceSpec(COUNTER, 2147483647, 0, null, "PARTY", "hand-built probe", null)));
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
