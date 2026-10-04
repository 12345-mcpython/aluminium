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

/** Which field stops the repeat? */
public class StackTimesVariantsTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COUNTER = "probeParty";
    private static final String STATE = "probeGift";

    @Test
    public void theFieldsAreCompared() {
        int a = instances(shape(true, 99, true, false));
        int b = instances(shape(false, Integer.MAX_VALUE, true, false));
        int c = instances(shape(false, Integer.MAX_VALUE, false, false));
        int d = instances(shape(false, 99, false, false));
        System.out.println("[variants] A(permanent,99,scale)=" + a + " B(turns2,MAX,scale)=" + b
                + " C(turns2,MAX,amount)=" + c + " D(turns2,99,amount)=" + d);
        Assertions.assertEquals(7, a, "A is the known-good shape");
        Assertions.assertEquals(7, b, "B: a two-turn state with an unbounded cap");
        Assertions.assertEquals(7, c, "C: a literal count with an unbounded cap");
        Assertions.assertEquals(7, d, "D: a literal count with a small cap");
    }

    // ==================================================================

    private static int instances(EffectSpec apply) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        EffectSpec grant = effect("GAIN_RESOURCE", "resource", COUNTER, "amount", 7.0);
        owner.setTriggerTable(new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), grant, apply)),
                List.of(new ResourceSpec(COUNTER, 2147483647, 0, null, "PARTY", "hand-built probe", null))));
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return owner.getBuffManager().stacksOf(STATE);
    }

    private static EffectSpec shape(boolean permanent, int cap, boolean scale, boolean unused) {
        EffectSpec spec = effect("APPLY_BUFF", "buff", STATE, "target", "self",
                "stackable", Boolean.TRUE, "maxStacks", cap);
        if (permanent) {
            TriggerSpecs.set(spec, "permanent", Boolean.TRUE);
        } else {
            TriggerSpecs.set(spec, "turns", 2);
        }
        if (scale) {
            TriggerSpecs.set(spec, "scale", "party_resource:" + COUNTER);
            TriggerSpecs.set(spec, "percent", 1.0);
        } else {
            TriggerSpecs.set(spec, "amount", 4.0);
        }
        return spec;
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
