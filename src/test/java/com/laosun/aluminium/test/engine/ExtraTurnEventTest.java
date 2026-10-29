package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The extra-turn boundary events: {@code EXTRA_TURN_STARTED} and {@code EXTRA_TURN_ENDED}.
 *
 * <p><b>Why the pair had to exist.</b> Nothing on the battlefield says "this turn is an extra one": {@code Queue}
 * holds it as PENDING and {@code moveExtraTurn()} consumes that flag as it hands the turn over, so from the turn's
 * own {@code TURN_START} onward the engine has already forgotten. A clause about such a turn ("at the start of this
 * extra turn, ... which is removed at the end of the extra turn" - 1503) therefore had no moment to hang on.
 *
 * <p><b>What each case pins.</b>
 * <ul>
 *   <li>the pair fires <b>once each</b>, on the unit that took the extra turn, and in the right ORDER: the start at
 *       the move that hands the turn over (before the unit acts), the end after the turn has been acted out. The
 *       first version of the engine change fired both at the same move - this case is what caught it;</li>
 *   <li>an ORDINARY turn fires neither, so the readings below are about extra turns and not about turn boundaries
 *       in general;</li>
 *   <li>and a grant that never becomes a turn emits neither.</li>
 * </ul>
 *
 * <p><b>The fixture counts with ordinary self-scoped counters named after Pearl's own resources</b> (好活当赏 /
 * 笑点) rather than inventing new names: this judges the ENGINE boundary, so it must not depend on the content rule
 * that will use it, and it needs no new fixture shape. Note: both counters are SELF-scoped here even though 笑点 is a
 * PARTY resource in the shipped content - what is being counted is "how many times did the event fire on this unit",
 * and a party-scoped counter needs a declaring file and reads 0 without one (which is how the first version of this
 * fixture failed silently). Note: the counting table is installed on the unit that RECEIVES the extra turn, so the
 * readings also pin who the event's {@code actor} is.
 */
public class ExtraTurnEventTest {
    private static final int PEARL = 1503;          // used as the fixture character, and for the counters' names
    private static final int ALLY = 1502;           // a second real unit, and a legal Elation ally
    private static final String COUNTER = "好活当赏";
    private static final String LAUGH = "笑点";
    private static final double EPS = 1e-9;

    /**
     * The pair fires once each, in order, on the unit taking the extra turn.
     *
     * <p>A complete turn is driven for the end (`beforeMove` + `afterMove`) because the end IS a turn boundary:
     * reading it right after the move would prove nothing about when it happens.
     */
    @Test
    public void thePairFiresOnceEachInOrderOnTheUnitTakingTheExtraTurn() {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        installCounters(ally);
        Battle battle = new Battle(List.of(pearl, ally), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        // One ordinary turn first: it must move nothing, so the readings below are about EXTRA turns.
        runOneFullTurn(battle);
        Assertions.assertEquals(0, ally.getResources().value(COUNTER), EPS,
                "an ordinary turn is not an extra turn: no start event");
        Assertions.assertEquals(0, ally.getResources().value(LAUGH), EPS, "and no end event either");

        // Note: The grant comes AFTER a finished turn, not in the middle of one. `afterMove()` reschedules the
        // actor, and the extra turn's "original schedule" is the value at the moment of the grant - so granting
        // before the turn is finished and then stepping once reads the extra turn as never having happened
        // (measured while writing this: the start event fired, the end event never did, because the grant was
        // re-issued from a turn that was still open).
        Assertions.assertTrue(battle.grantExtraTurn(ally), "precondition: the extra turn was scheduled");
        System.out.println("[probe] scheduled=" + battle.getExtraTurnActor()
                + " ; counter registered=" + ally.getResources().has(COUNTER));
        battle.stepForward();
        System.out.println("[probe] after the move: current=" + battle.currentMove().getCanHit().getName()
                + " counter=" + ally.getResources().value(COUNTER));
        Assertions.assertEquals(ally, battle.currentMove().getCanHit(),
                "precondition: the extra turn is the ally's");
        Assertions.assertEquals(1, ally.getResources().value(COUNTER), EPS,
                "EXTRA_TURN_STARTED fired ONCE, at the move that hands the turn over (before the unit acts)");

        battle.beforeMove();
        battle.afterMove();
        Assertions.assertEquals(1, ally.getResources().value(LAUGH), EPS,
                "EXTRA_TURN_ENDED fired ONCE, after the turn was acted out - not at the move that started it");
        Assertions.assertEquals(1, ally.getResources().value(COUNTER), EPS,
                "and the start did not fire a second time");
    }

    /**
     * The end is not "the same move as the start": an ordinary turn after the extra one must not announce an end.
     */
    @Test
    public void anOrdinaryTurnAfterwardsAnnouncesNoEnd() {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        installCounters(ally);
        Battle battle = new Battle(List.of(pearl, ally), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        Assertions.assertTrue(battle.grantExtraTurn(ally));
        battle.stepForward();
        battle.beforeMove();
        battle.afterMove();
        int afterTheExtraTurn = ally.getResources().value(LAUGH);
        Assertions.assertEquals(1, afterTheExtraTurn, "precondition: the extra turn ended once");
        // Everything from here on is ordinary turn traffic.
        runOneFullTurn(battle);
        runOneFullTurn(battle);
        Assertions.assertEquals(afterTheExtraTurn, ally.getResources().value(LAUGH), EPS,
                "no further end events: the extra turn ended exactly once");
    }

    /**
     * A grant that never becomes a turn emits nothing.
     */
    @Test
    public void aGrantThatNeverBecomesATurnEmitsNothing() {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        installCounters(ally);
        Character outsider = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(pearl, ally), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(battle.grantExtraTurn(outsider),
                "precondition: a unit that is not in the action order cannot receive an extra turn");
        runOneFullTurn(battle);
        Assertions.assertEquals(0, ally.getResources().value(LAUGH), EPS,
                "no extra turn happened, so neither boundary may announce one");
        Assertions.assertEquals(0, ally.getResources().value(COUNTER), EPS, "and no start either");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Counts the two events on {@code unit}, with Pearl's declared resources.
     *
     * <p>Two DIFFERENT resources for two events on purpose: one counter for both would leave "did the start fire
     * twice" and "did the end fire at all" indistinguishable.
     *
     * <p>Note: Both resources are registered HERE as well, and that is not belt-and-braces. `CharacterFactory`
     * registers a character's resources from the trigger table it was built with, and this fixture REPLACES that
     * table - so without these two lines an id that is never registered reads 0 forever, with no error anywhere
     * (measured: `resources.has(好活当赏)` came back false and every reading was a silent 0).
     *
     * <p>Note: No {@code actor == self} condition is needed - a trigger table belongs to the unit it is installed on
     * and is only consulted for that unit, so "the ally's table" already IS "the ally".
     */
    private static void installCounters(Character unit) {
        unit.setTriggerTable(new TriggerTable(unit.getCid(), List.of(
                TriggerSpecs.rule(TriggerEvent.EXTRA_TURN_STARTED.name(), List.of(), gain(COUNTER)),
                TriggerSpecs.rule(TriggerEvent.EXTRA_TURN_ENDED.name(), List.of(), gain(LAUGH)))));
        if (!unit.getResources().has(COUNTER)) {
            unit.getResources().register(COUNTER, 50, 0);
        }
        if (!unit.getResources().has(LAUGH)) {
            unit.getResources().register(LAUGH, Integer.MAX_VALUE, 0);
        }
    }

    private static EffectSpec gain(String resource) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(effect, "resource", resource);
        TriggerSpecs.set(effect, "amount", 1.0);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    /**
     * Drives one complete ordinary turn: settle the actor's start, then finish it.
     */
    private static void runOneFullTurn(Battle battle) {
        battle.stepForward();
        battle.beforeMove();
        battle.afterMove();
    }
}
