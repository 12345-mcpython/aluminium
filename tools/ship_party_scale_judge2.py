"""Judge the party-resource scale through the path that honours `scale` (round 1682).

Measured just now: `ADD_STACK` reads only `event_amount` from `scale` (`wanted = effect.getAmount() == null ? 1 : …`), so a
party counter fed to it produced ONE stack -- a real finding, registered separately. `MODIFY_ATTR`'s derived path does honour
`scale`, so that is where this capability is read: `percent x party counter` becomes an absolute magnitude.

Seven on the counter, `percent: 0.01` -> the attribute must rise by exactly 0.07.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/PartyResourceScaleTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.enums.AttributeType;
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
 *
 * <p>\\u26a0 Read through MODIFY_ATTR, because measured: ADD_STACK honours only `event_amount` from `scale`. That gap is
 * registered on its own.
 */
public class PartyResourceScaleTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COUNTER = "probeParty";

    /** Seven on the counter, and 0.01 x 7 = 0.07 added to the attribute. */
    @Test
    public void theMagnitudeComesFromThePartyCounter() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(scene());
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double after = owner.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[party-scale] counter=" + battle.partyResourceValue(COUNTER) + " attack=" + after);
        Assertions.assertEquals(7, battle.partyResourceValue(COUNTER), "precondition: seven on the counter");
        // the attribute started at its own value with no modifiers from this table, so the delta is the magnitude
        Assertions.assertEquals(0.07, after - BASE, 1e-9,
                "0.01 x the party counter's 7 -- the counter is what the magnitude read");
    }

    private static final double BASE = 633.183157894737;

    // ==================================================================

    private static TriggerTable scene() {
        EffectSpec grant = effect("GAIN_RESOURCE", "resource", COUNTER, "amount", 7.0);
        EffectSpec derived = effect("MODIFY_ATTR", "attribute", "ATTACK",
                "scale", "party_resource:" + COUNTER, "percent", 0.01,
                "permanent", Boolean.TRUE, "target", "self");
        return new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), grant, derived)),
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
print("ok   judge rewritten around the derived path")
