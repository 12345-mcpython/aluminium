"""The stackable-state judge, built the way the probe proved works (item 60).

Measured this round: ONE BATTLE_START rule carrying THREE apply effects leaves three instances (both readers agree); the
earlier attempts used three separate rules of the same event, and only one of them fired -- which is what produced the
zeroes, not the capability.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/StackableStateTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StackableStateBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A state that carries a COUNT (2026-10-02): "stackable": true on APPLY_BUFF.
 *
 * <p>A plain state refreshes on re-application, and an ADD_STACK buff accumulates but is not a StateBuff, so its expiry
 * never announces STATE_ENDED. The corpus needs both at once for Bondmate-of-Appreciation ("the laugh points are counted
 * into that state"), which is what 1505's "turns 50% of it into her own" then reads.
 *
 * <p>⚠ Three effects in ONE rule on purpose: measured, three separate rules of the same event fire only one of them, which
 * reads as zero instances and has nothing to do with this capability.
 */
public class StackableStateTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String STATE = "probeState";
    private static final String RECORD = "probeRecord";

    /** Three applications leave three instances -- that is the count. */
    @Test
    public void theSameStateStacksInsteadOfRefreshing() {
        Character owner = owner();
        System.out.println("[stackable] asStackable=" + owner.getBuffManager().countBuffs(StackableStateBuff.class)
                + " stacksOf=" + owner.getBuffManager().stacksOf(STATE));
        Assertions.assertEquals(3, owner.getBuffManager().stacksOf(STATE),
                "three applications, three instances");
    }

    /** And ending it announces the moment once per instance. */
    @Test
    public void endingItAnnouncesOncePerInstance() {
        Character owner = owner();
        Battle battle = battle(owner);
        int removed = owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        System.out.println("[stackable] removed=" + removed
                + " announcements=" + owner.getResources().value(RECORD));
        Assertions.assertEquals(3, removed, "three instances come off");
        Assertions.assertEquals(3, owner.getResources().value(RECORD), "one announcement per instance");
    }

    // ==================================================================

    private static Character owner() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(scene());
        return owner;
    }

    private static Battle battle(Character owner) {
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static TriggerTable scene() {
        EffectSpec record = effect("GAIN_RESOURCE", "resource", RECORD, "amount", 1.0);
        return new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), stack(), stack(), stack()),
                        TriggerSpecs.rule("STATE_ENDED", List.of("self state_ended " + STATE), record)),
                List.of(new ResourceSpec(RECORD, 2147483647, 0, null, null, "hand-built probe", null)));
    }

    private static EffectSpec stack() {
        return effect("APPLY_BUFF", "buff", STATE, "turns", 9, "stackable", Boolean.TRUE,
                "maxStacks", 9, "target", "self");
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
print("ok   judge written the way the probe proved works")
