"""Judge for the party-resource scale (item 69), in the hand-built-table pattern the shipped probes use.

Scene: a table that declares a PARTY-scoped counter, grants it 7, and then stacks a buff with
`scale: "party_resource:<that counter>"` and `percent: 1` -- so the buff's stack count must be exactly 7. The count lives on
the battle, so no unit's own stack vocabulary could have read it.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/PartyResourceScaleTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.models.Character;
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
 * <p>The reader is 1513's reward: it must hand 【好活当赏】 the number of 【笑点】 the Aha moment spent, and 【笑点】 is declared
 * `scope: PARTY` -- the battle owns it, so `self_stacks:` (which reads a unit) cannot reach it.
 */
public class PartyResourceScaleTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COUNTER = "probeParty";
    private static final String STACK = "probeStack";

    /** Seven on the party counter, seven stacks on the unit. */
    @Test
    public void theStackCountComesFromThePartyCounter() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(scene());
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        System.out.println("[party-scale] counter=" + battle.partyResourceValue(COUNTER)
                + " stacks=" + owner.getBuffManager().stacksOf(STACK));
        Assertions.assertEquals(7, battle.partyResourceValue(COUNTER), "precondition: seven on the counter");
        Assertions.assertEquals(7, owner.getBuffManager().stacksOf(STACK),
                "the stacks are the party counter's number");
    }

    // ==================================================================

    private static TriggerTable scene() {
        EffectSpec grant = effect("GAIN_RESOURCE", "resource", COUNTER, "amount", 7.0);
        EffectSpec stack = effect("ADD_STACK", "buff", STACK, "amount", 1.0,
                "scale", "party_resource:" + COUNTER, "percent", 1.0,
                "maxStacks", 99, "permanent", Boolean.TRUE, "target", "self");
        return new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), grant, stack)),
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
''')
print("ok   judge written")
