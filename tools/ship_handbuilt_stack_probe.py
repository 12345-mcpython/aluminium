"""Patch-free probe (round 1657): does a second RULE evict the first rule's stack, with shipped vocabulary?

The rollback left one unknown: a capability that attaches once, but reads zero as soon as there is a second application.
Two causes fit that: (a) something in the hand-built table plumbing replaces/refreshes, or (b) something specific to the
class that was rolled back. This probe asks the same question with ADD_STACK -- the accumulate path that already ships --
and needs no engine change at all.

  * scene A: ONE BATTLE_START rule, one ADD_STACK;
  * scene B: TWO such rules, each with one ADD_STACK.

If B reads 1, the plumbing replaces and the rolled-back class was never the problem; if B reads 2, stacking through
hand-built tables is fine and the class was. ASCII-only Java strings.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/HandBuiltStackProbeTest.java"

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

    // ==================================================================

    private static int stacksAfter(int rules) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        List<com.laosun.aluminium.beans.TriggerSpec> specs = new java.util.ArrayList<>();
        for (int i = 0; i < rules; i++) {
            specs.add(TriggerSpecs.rule("BATTLE_START", List.of(), stack()));
        }
        owner.setTriggerTable(new TriggerTable(OWNER, specs));
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
''')
print("ok   patch-free probe written")
