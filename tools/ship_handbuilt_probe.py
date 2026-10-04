"""Discriminator (round 1656): does a HAND-BUILT table's APPLY_BUFF attach a plain state at all?

The rollback left one question open: the stackable-state judge read 0 instances everywhere, and it could not be told apart
from the possibility that a hand-built table never applies a state in the first place. This test changes nothing in the
engine -- it builds the simplest possible table (BATTLE_START -> APPLY_BUFF of one plain state) and asks whether the state
is there. ASCII-only Java strings.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/HandBuiltApplyBuffProbeTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A probe, not a spec: can a hand-built table apply a plain state?
 *
 * <p>Why it exists: the stackable-state attempt read zero instances for every reading, and "the new spelling is wrong" and
 * "a hand-built table never applies a state" both explain that. This test separates them without touching the engine.
 */
public class HandBuiltApplyBuffProbeTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String STATE = "probeState";

    /** The state is on the unit after battle start. */
    @Test
    public void aHandBuiltTableAppliesAPlainState() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        EffectSpec apply = new EffectSpec();
        TriggerSpecs.set(apply, "op", "APPLY_BUFF");
        TriggerSpecs.set(apply, "buff", STATE);
        TriggerSpecs.set(apply, "turns", 9);
        TriggerSpecs.set(apply, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), apply))));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        System.out.println("[probe] hasState=" + owner.getBuffManager().hasState(STATE)
                + " stacksOf=" + owner.getBuffManager().stacksOf(STATE));
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE),
                "a hand-built table has to be able to apply a plain state");
    }
}
''')
print("ok   probe written")
