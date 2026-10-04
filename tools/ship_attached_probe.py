"""Instrumented probe: print the buff that is ACTUALLY attached, not the EffectSpec's fields.

The named next idea after the rollback. Previous rounds printed the spec (which was correct) and the counts (which were
zero); this one asks the manager for the real instances and prints their class, isStackable(), stackGroupKey(), maxStacks()
and getBuffName(). One application only, so the number under test is 1.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/AttachedBuffProbeTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.buff.StackableStateBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** What is really on the unit after one stackable APPLY_BUFF? */
public class AttachedBuffProbeTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String STATE = "probeState";

    @Test
    public void theAttachedBuffIsTheStackableOne() {
        EffectSpec apply = new EffectSpec();
        TriggerSpecs.set(apply, "op", "APPLY_BUFF");
        TriggerSpecs.set(apply, "buff", STATE);
        TriggerSpecs.set(apply, "turns", 9);
        TriggerSpecs.set(apply, "target", "self");
        TriggerSpecs.set(apply, "stackable", Boolean.TRUE);
        TriggerSpecs.set(apply, "maxStacks", 9);

        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), apply))));
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        List<AbstractBuff> attached = owner.getBuffManager().allBuffsOf(AbstractBuff.class);
        System.out.println("[probe] attached=" + attached.size()
                + " stackableClass=" + owner.getBuffManager().countBuffs(StackableStateBuff.class)
                + " hasState=" + owner.getBuffManager().hasState(STATE));
        for (AbstractBuff buff : attached) {
            System.out.println("[probe] buff class=" + buff.getClass().getSimpleName()
                    + " stackable=" + buff.isStackable()
                    + " key=" + buff.stackGroupKey()
                    + " maxStacks=" + buff.maxStacks()
                    + " name=" + buff.getBuffName());
        }
        Assertions.assertFalse(attached.isEmpty(), "something has to be on the unit");
    }
}
''')
print("ok   instrumented probe written")
