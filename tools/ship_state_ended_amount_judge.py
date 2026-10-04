"""Judge for precondition ② (item 61): STATE_ENDED carries how much ended, announced once per sweep.

The scene uses the layout the stackable-state round proved works (ONE rule carrying three effects) plus one STATE_ENDED
rule that moves the event's amount into a declared resource; the state is ended from Java, so no turn machinery is involved.
Expected: three instances stack, and one sweep records 3 -- not 3+2+1, and not 0.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/StateEndedAmountTest.java"

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
 * STATE_ENDED carries HOW MUCH ended (2026-10-02).
 *
 * <p>The reader it exists for is 1505's "when a teammate's Bondmate-of-Appreciation ends, Evanescia turns 50% of it into her
 * own": "50% of it" needs the event to say how much "it" was. The count is the number of instances of the state, which is
 * what a stackable state accumulates.
 *
 * <p>ONE announcement per sweep: the head of removeState counts the instances first and fires once, so the recorded amount
 * is 3 and not 3+2+1 over three firings.
 */
public class StateEndedAmountTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String STATE = "probeState";
    private static final String RECORD = "probeRecord";

    /** Three instances stack, and ending them records the total once. */
    @Test
    public void theEventCarriesHowMuchEnded() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(scene());
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Assertions.assertEquals(3, owner.getBuffManager().stacksOf(STATE), "precondition: three instances");

        int removed = owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        int recorded = owner.getResources().value(RECORD);
        System.out.println("[STATE_ENDED] removed=" + removed + " recorded=" + recorded);
        Assertions.assertEquals(3, removed, "three come off");
        Assertions.assertEquals(3, recorded, "and the event says three, once");
    }

    // ==================================================================

    private static TriggerTable scene() {
        EffectSpec record = effect("GAIN_RESOURCE", "resource", RECORD, "amountFromEvent", Boolean.TRUE);
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
print("ok   judge written")
