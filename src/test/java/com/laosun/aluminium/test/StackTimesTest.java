package com.laosun.aluminium.test;

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
        // ⚠ Measured: `stacksOf` counts NAMED instances, and a plain StateBuff carries no name -- so it answers 0 for the
        // control either way. `hasState` is what the control's claim is about: the state is there, once, unrepeated.
        System.out.println("[stack-times] plain stacksOf=" + owner.getBuffManager().stacksOf(STATE)
                + " hasState=" + owner.getBuffManager().hasState(STATE));
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "the plain state is on the unit");
        Assertions.assertEquals(0, owner.getBuffManager().stacksOf(STATE),
                "and it holds no named instance -- which is why a plain state cannot carry a count");
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
        // ⚠ `max_stacks` is only allowed beside `stackable` (item 60), and the scale only matters for a state that can
        // hold a count -- so the control states neither.
        EffectSpec apply = stackable
                ? effect("APPLY_BUFF", "buff", STATE, "permanent", Boolean.TRUE, "target", "self",
                        "stackable", Boolean.TRUE, "maxStacks", 99,
                        "scale", "party_resource:" + COUNTER, "percent", 1.0)
                : effect("APPLY_BUFF", "buff", STATE, "permanent", Boolean.TRUE, "target", "self");
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
