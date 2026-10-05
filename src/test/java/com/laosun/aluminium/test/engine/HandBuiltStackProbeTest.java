package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
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

/** How many stacks survive when they come from one rule vs two? */
public class HandBuiltStackProbeTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String STACK = "probeStack";

    /** Baseline: one rule, one application. */
    @Test
    public void oneRuleGivesOne() {
        int stacks = stacksAfter(1);
        System.out.println("[probe] rules=1 stacks=" + stacks);
        Assertions.assertEquals(1, stacks, "one application is one stack");
    }

    /** The question: does a second rule add a second stack, or replace the first? */
    @Test
    public void twoRulesGiveHowMany() {
        int stacks = stacksAfter(2);
        System.out.println("[probe] rules=2 stacks=" + stacks);
        Assertions.assertEquals(2, stacks,
                "if this fails with 1, the hand-built-table plumbing replaces instead of accumulating");
    }

    /** The third variable: the same two rules, but the table also DECLARES a resource. */
    @Test
    public void twoRulesWithADeclaredResource() {
        int stacks = stacksAfter(2, true, false);
        System.out.println("[probe] rules=2 withResource=true stacks=" + stacks);
        Assertions.assertEquals(2, stacks, "declaring a resource must not empty the table");
    }

    /** And with a STATE_ENDED rule as well, which is what the rolled-back judge had. */
    @Test
    public void twoRulesPlusAStateEndedRule() {
        int stacks = stacksAfter(2, true, true);
        System.out.println("[probe] rules=2 withResource=true withStateEndedRule=true stacks=" + stacks);
        Assertions.assertEquals(2, stacks, "an extra subscriber must not empty the table");
    }

    // ==================================================================

    private static int stacksAfter(int rules) {
        return stacksAfter(rules, false, false);
    }

    private static int stacksAfter(int rules, boolean withResource, boolean withStateEndedRule) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        List<com.laosun.aluminium.beans.TriggerSpec> specs = new java.util.ArrayList<>();
        for (int i = 0; i < rules; i++) {
            specs.add(TriggerSpecs.rule("BATTLE_START", List.of(), stack()));
        }
        if (withStateEndedRule) {
            EffectSpec record = new EffectSpec();
            TriggerSpecs.set(record, "op", "GAIN_RESOURCE");
            TriggerSpecs.set(record, "resource", "probeRecord");
            TriggerSpecs.set(record, "amount", 1.0);
            specs.add(TriggerSpecs.rule("STATE_ENDED", List.of("self state_ended " + STACK), record));
        }
        if (withResource) {
            owner.setTriggerTable(new TriggerTable(OWNER, specs,
                    List.of(new com.laosun.aluminium.beans.ResourceSpec("probeRecord", 2147483647, 0,
                            null, null, "hand-built probe", null))));
        } else {
            owner.setTriggerTable(new TriggerTable(OWNER, specs));
        }
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return owner.getBuffManager().stacksOf(STACK);
    }

    private static EffectSpec stack() {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", "ADD_STACK");
        TriggerSpecs.set(spec, "buff", STACK);
        TriggerSpecs.set(spec, "amount", 1.0);
        TriggerSpecs.set(spec, "maxStacks", 9);
        TriggerSpecs.set(spec, "permanent", Boolean.TRUE);
        TriggerSpecs.set(spec, "target", "self");
        return spec;
    }
}
