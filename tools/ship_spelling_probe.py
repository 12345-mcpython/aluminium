"""Read-back probe: is the new spelling actually set, and does the stackable state attach?

It prints three things the previous attempt could not separate: the value the EffectSpec carries, whether a state of that
name is on the unit, and how many instances of the new class are attached. ASCII-only Java strings.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/StackableSpellingProbeTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StackableStateBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Where does the stackable state get lost: the spelling, the arm, or the attachment? */
public class StackableSpellingProbeTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String STATE = "probeState";

    @Test
    public void theSpellingIsSetAndTheStateAttaches() {
        EffectSpec apply = new EffectSpec();
        TriggerSpecs.set(apply, "op", "APPLY_BUFF");
        TriggerSpecs.set(apply, "buff", STATE);
        TriggerSpecs.set(apply, "turns", 9);
        TriggerSpecs.set(apply, "target", "self");
        TriggerSpecs.set(apply, "stackable", Boolean.TRUE);
        TriggerSpecs.set(apply, "maxStacks", 9);

        System.out.println("[probe] readback stackable=" + apply.getStackable()
                + " maxStacks=" + apply.getMaxStacks() + " op=" + apply.getOp());

        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), apply))));
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        System.out.println("[probe] hasState=" + owner.getBuffManager().hasState(STATE)
                + " stacksOf=" + owner.getBuffManager().stacksOf(STATE)
                + " stackableClass=" + owner.getBuffManager().countBuffs(StackableStateBuff.class));
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "the state has to attach");
    }
}
''')
print("ok   read-back probe written")
