package com.laosun.aluminium.test;

import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.TriggerTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Relic set 324, 2-piece (2026-09-30): the same-turn skill-point threshold and the count that pays it out.
 *
 * <p>⚠ Two measured lessons are baked in here. First, this reads the rules with {@code rulesFor(event)}, NOT with
 * {@code matching(event, ctx)}: matching EVALUATES the conditions, so a context with no stacks answers the payout rule's
 * ">= 4" with false and the list comes back one short (measured). Second, the literals carry the {@code .0} the parser
 * writes for an integral threshold -- that law has now bitten four times, so it is written in from the start.
 *
 * <p>The 2-piece CRIT DMG 16% is the data's PropertyList (CriticalDamageBase) and is deliberately absent from the file,
 * the convention set 311 follows.
 */
public class Relic324Test {
    private static final int SET = 324;
    private static final String COUNTER = "本回合消耗";

    private static List<TriggerTable.CompiledRule> rules() {
        return RelicTriggerTables.of(SET).at(2).rulesFor(TriggerEvent.SKILL_POINT_SPENT);
    }

    @Test
    public void theTurnCounterAndItsPayoutAreBothThere() {
        var rules = rules();
        Assertions.assertEquals(2, rules.size(), "a counting rule and a paying rule");

        var counting = rules.get(0);
        Assertions.assertEquals(List.of("self_stacks:" + COUNTER + " < 4.0"),
                counting.conditions().stream().map(c -> c.source()).toList(),
                "count while under four, in the parser's form");
        var add = counting.effects().getFirst();
        Assertions.assertEquals("ADD_STACK", add.getOp());
        Assertions.assertEquals(COUNTER, add.getBuff(), "the counter's name");
        Assertions.assertEquals(1.0, add.getAmount(), 1e-9, "one point per spend");
        Assertions.assertEquals(4, add.getMaxStacks(), "capped at four");

        var paying = rules.get(1);
        Assertions.assertEquals(List.of("self_stacks:" + COUNTER + " >= 4.0"),
                paying.conditions().stream().map(c -> c.source()).toList(),
                "pay out at four, in the parser's form");
        var effects = paying.effects();
        Assertions.assertEquals(2, effects.size(), "reset, then the bonus");
        Assertions.assertEquals("REMOVE_STACK", effects.get(0).getOp());
        Assertions.assertEquals(COUNTER, effects.get(0).getBuff());
        Assertions.assertEquals(4.0, effects.get(0).getAmount(), 1e-9, "reset by four, so it is every four");
        Assertions.assertEquals("MODIFY_ATTR", effects.get(1).getOp());
        Assertions.assertEquals("CRIT_ATTACK", effects.get(1).getAttribute());
        Assertions.assertEquals(0.16, effects.get(1).getPercent(), 1e-9, "the bonus");
        Assertions.assertEquals(3, effects.get(1).getTurns(), "three turns");
        Assertions.assertEquals("self", effects.get(1).getTarget());
        System.out.println("[relic324] ok");
    }
}
