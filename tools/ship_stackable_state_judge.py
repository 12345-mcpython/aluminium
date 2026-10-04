"""Judge for a STACKABLE state (item 60). Judge-only, ASCII-only Java strings.

Two readings:
  * re-applying the same state three times leaves THREE instances (a plain state would refresh to one);
  * ending it announces STATE_ENDED once per instance -- the count is what a reader like 1505's will eventually take.

Instrument: a hand-built table with its own declared resource that counts the announcements, so nothing depends on shipped
content.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/StackableStateTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
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
 * A state that carries a COUNT (2026-10-02): "stackable": true on APPLY_BUFF.
 *
 * <p>Measured before this existed: a plain state refreshes on re-application (one instance, however many times it is
 * applied) and an ADD_STACK buff accumulates but is not a StateBuff, so its expiry never announces STATE_ENDED. The
 * corpus needs both at once for Bondmate-of-Appreciation ("the laugh points are counted into that state").
 */
public class StackableStateTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String STATE = "probeState";
    private static final String RECORD = "probeRecord";

    /** Re-applying it three times leaves three instances -- that is the count. */
    @Test
    public void theSameStateStacksInsteadOfRefreshing() {
        Character owner = owner();
        Assertions.assertEquals(3, owner.getBuffManager().stacksOf(STATE),
                "three applications, three instances");
    }

    /** And when it ends, the moment is announced once per instance. */
    @Test
    public void endingItAnnouncesOncePerInstance() {
        Character owner = owner();
        Battle battle = battle(owner);
        endIt(battle);
        int announced = owner.getResources().value(RECORD);
        System.out.println("[stackable state] instances=" + owner.getBuffManager().stacksOf(STATE)
                + " announcements=" + announced);
        Assertions.assertEquals(3, announced, "one announcement per instance");
        Assertions.assertEquals(0, owner.getBuffManager().stacksOf(STATE), "and none is left");
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

    private static void endIt(Battle battle) {
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == battle.allies.getFirst()).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: the owner is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
    }

    private static TriggerTable scene() {
        EffectSpec apply = effect("APPLY_BUFF", "buff", STATE, "turns", 9, "stackable", Boolean.TRUE,
                "maxStacks", 9, "target", "self");
        EffectSpec endState = effect("REMOVE_STATE", "buff", STATE, "target", "self");
        EffectSpec record = effect("GAIN_RESOURCE", "resource", RECORD, "amount", 1.0);
        return new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), apply, apply, apply),
                        TriggerSpecs.rule("TURN_START", List.of(), endState),
                        TriggerSpecs.rule("STATE_ENDED", List.of("self state_ended " + STATE), record)),
                List.of(new ResourceSpec(RECORD, 2147483647, 0, null, null, "hand-built probe", null)));
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
print("ok   judge written (ASCII-only Java strings)")
