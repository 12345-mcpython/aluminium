"""Judge for the magnitude STATE_ENDED now carries (item 59). Judge-only, ASCII-only Java strings.

Instrument: a hand-built table on one character with
  * a resource that RECORDS what arrives,
  * BATTLE_START applying the same state THREE times (three instances = the engine's spelling of "the state carries a count
    of three", which is what the corpus means by "the laugh points are counted into that state"),
  * a TURN_START that ends the state explicitly,
  * and a STATE_ENDED rule that moves the event's amount into the resource.
With the patch the recorded value is 3; before it the event's amount was hard-coded 0.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/StateEndedAmountTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.enums.TriggerEvent;
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
 * STATE_ENDED carries HOW MANY instances of the ended state the carrier held (2026-10-02).
 *
 * <p>The reader it exists for is 1505's "when a teammate's Bondmate-of-Appreciation ends, Evanescia turns 50% of it into
 * her own" -- "50% of it" needs the event to say how much "it" was. The engine spells the count as stacked instances of
 * the state, which is also how the corpus describes it ("the laugh points are counted INTO that state").
 *
 * <p>Three instances are applied at battle start and the state is ended in one sweep, so the first firing must see 3.
 */
public class StateEndedAmountTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String STATE = "probe-state";
    private static final String RECORD = "probe-record";

    /** The recorded amount is the number of instances that were on the carrier. */
    @Test
    public void theEventCarriesHowManyInstancesEnded() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(scene());
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Assertions.assertEquals(3, owner.getBuffManager().stacksOf(STATE), "precondition: three instances are on it");

        // one whole turn: TURN_START ends the state explicitly, which is the sweep that fires STATE_ENDED
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == own(battle, owner)).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: it is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();

        int recorded = owner.getResources().value(RECORD);
        System.out.println("[STATE_ENDED] recorded amount=" + recorded
                + " ; instances left=" + owner.getBuffManager().stacksOf(STATE));
        Assertions.assertEquals(3, recorded, "the event has to say how many instances ended");
    }

    // ==================================================================

    private static Character own(Battle battle, Character expected) {
        return (Character) battle.allies.getFirst();
    }

    private static TriggerTable scene() {
        EffectSpec endState = effect("REMOVE_STATE", "buff", STATE, "target", "self");
        EffectSpec record = effect("GAIN_RESOURCE", "resource", RECORD, "amountFromEvent", Boolean.TRUE);
        EffectSpec apply = effect("APPLY_BUFF", "buff", STATE, "turns", 9, "target", "self");
        TriggerTable table = new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), apply, apply, apply),
                        TriggerSpecs.rule("TURN_START", List.of(), endState),
                        TriggerSpecs.rule("STATE_ENDED", List.of("self state_ended " + STATE), record)),
                List.of(new ResourceSpec(RECORD, 2147483647, 0, null, null, "hand-built probe", null)));
        return table;
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
