"""Judge for "N instances at once" (item 70), in the hand-built-table pattern.

A PARTY counter is granted 7, then a STACKABLE state is applied with `scale: party_resource:<counter>` and `percent: 1` -- so
the unit must carry exactly seven instances. That count is what 1505's clause reads when the state ends, which is why this
piece closes the bridge: `1513` can now hand 【好活当赏】 the 【笑点】 the Aha moment spent.

The second reading is the guard: without `stackable`, the same effect must leave ONE instance (re-applying a plain state
refreshes it), so the feature cannot silently apply to states that cannot hold a count.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/StackTimesTest.java"

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
 * A stackable state can be applied N times in one effect (2026-10-02).
 *
 * <p>The reader is 1513's reward, whose state's instance count IS the 【笑点】 it spent -- and 1505's 「开不败」 reads that count
 * when the state ends.
 */
public class StackTimesTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COUNTER = "probeParty";
    private static final String STATE = "probeGift";

    /** Seven on the counter, seven instances on the unit. */
    @Test
    public void theCountComesFromThePartyCounter() {
        Character owner = owner(true);
        System.out.println("[stack-times] stackable instances=" + owner.getBuffManager().stacksOf(STATE));
        Assertions.assertEquals(7, owner.getBuffManager().stacksOf(STATE),
                "the instances are the party counter's number");
    }

    /** A plain state is NOT repeated: re-applying it refreshes, so the effect must stay one instance. */
    @Test
    public void aPlainStateStaysOne() {
        Character owner = owner(false);
        System.out.println("[stack-times] plain instances=" + owner.getBuffManager().stacksOf(STATE));
        Assertions.assertEquals(1, owner.getBuffManager().stacksOf(STATE),
                "only a stackable state can hold a count");
    }

    // ==================================================================

    private static Character owner(boolean stackable) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(scene(stackable));
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return owner;
    }

    private static TriggerTable scene(boolean stackable) {
        EffectSpec grant = effect("GAIN_RESOURCE", "resource", COUNTER, "amount", 7.0);
        EffectSpec apply = effect("APPLY_BUFF", "buff", STATE, "permanent", Boolean.TRUE, "target", "self",
                "stackable", stackable ? Boolean.TRUE : null,
                "maxStacks", 99,
                "scale", "party_resource:" + COUNTER, "percent", 1.0);
        return new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), grant, apply)),
                List.of(new ResourceSpec(COUNTER, 2147483647, 0, null, "PARTY", "hand-built probe", null)));
    }

    private static EffectSpec effect(String op, Object... pairs) {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", op);
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                TriggerSpecs.set(spec, (String) pairs[i], pairs[i + 1]);
            }
        }
        return spec;
    }
}
''')
print("ok   judge written")
