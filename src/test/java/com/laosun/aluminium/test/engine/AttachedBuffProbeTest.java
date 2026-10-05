package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
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
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), apply, apply, apply))));
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        int plain = owner.getBuffManager().countBuffs(
                com.laosun.aluminium.models.buff.StateBuff.class);
        int stackable = owner.getBuffManager().countBuffs(StackableStateBuff.class);
        System.out.println("[probe] asStateBuff=" + plain
                + " asStackableStateBuff=" + stackable
                + " stacksOf=" + owner.getBuffManager().stacksOf(STATE)
                + " hasState=" + owner.getBuffManager().hasState(STATE)
                + " specStackable=" + apply.getStackable());
        Assertions.assertEquals(3, stackable,
                "three applications, three stackable instances -- unless the instances really do vanish");
        Assertions.assertEquals(3, owner.getBuffManager().stacksOf(STATE),
                "and the name-counting reader has to see the same three");
    }
}
