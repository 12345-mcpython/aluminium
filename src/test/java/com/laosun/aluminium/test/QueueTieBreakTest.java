package com.laosun.aluminium.test;

import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tie-breaking on equal action values in the action bar (P7 fix E4).
 *
 * <p>Before the fix {@code Signal.compareTo} only compared {@code nextActionTime}, so when they were
 * equal the {@code PriorityQueue} order was **undefined** — "which of two units with the same speed
 * acts first" became a matter of luck, and {@code snapshot()} sorted the heap array stably, so the
 * **displayed order could disagree with the actual turn order**.
 *
 * <p>The fix: {@code Signal} records a globally increasing **schedule sequence number**, and
 * {@code compareTo} compares it when the action values are equal (the one scheduled earlier acts
 * first).
 */
public class QueueTieBreakTest {
    private static final double EPS = 1e-9;

    /**
     * The turn order of same-speed units must be the **entry order**, and must be reproducible —
     * running the same battle repeatedly gives the same result.
     *
     * <p>Two things are asserted: {@code peekNext()} returns the first one to enter; and the order
     * of consecutive {@code move()} calls agrees with the entry order. Before the fix these would
     * fail randomly with the heap's internal state.
     */
    @Test
    public void equalSpeedsActInEntryOrder() {
        Character first = character("first", 100);
        Character second = character("second", 100);
        Character third = character("third", 100);
        Queue q = new Queue(List.of(first, second, third));

        Assertions.assertEquals(first, q.peekNext(), "the one that entered first acts first");

        Assertions.assertEquals(first, nextActor(q));
        Assertions.assertEquals(second, nextActor(q));
        Assertions.assertEquals(third, nextActor(q));
    }

    /**
     * Running the same battle twice must give exactly the same turn order (determinism).
     *
     * <p>"Same speed + many units" is used to fill the heap with equal keys, giving the undefined
     * order a real chance to show itself.
     */
    @Test
    public void equalSpeedOrderIsDeterministicAcrossRuns() {
        List<String> firstRun = runEqualSpeedBatch(8);
        List<String> secondRun = runEqualSpeedBatch(8);

        Assertions.assertEquals(firstRun, secondRun, "two runs of the same setup must give the same order");
        Assertions.assertEquals(
                List.of("c0", "c1", "c2", "c3", "c4", "c5", "c6", "c7").subList(0, 8),
                firstRun.subList(0, 8),
                "the first round is exactly the entry order");
    }

    /**
     * The order of {@code snapshot()} must **equal** the actual turn order (E4's second symptom).
     */
    @Test
    public void snapshotOrderMatchesActualTurnOrder() {
        Character a = character("a", 100);
        Character b = character("b", 100);
        Character c = character("c", 100);
        Queue q = new Queue(List.of(a, b, c));

        List<CanHit> displayed = q.snapshot().stream().map(Signal::getCanHit).toList();
        List<CanHit> actual = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            actual.add(nextActor(q));
        }

        Assertions.assertEquals(actual, displayed, "the displayed order must equal the turn order");
    }

    /**
     * Action bar manipulation does **not** re-take a sequence number: pulling a unit to the same
     * action value as someone ahead of it still leaves it behind them.
     *
     * <p>This is a deliberately chosen semantics. If advancing also changed the sequence number, it
     * would be "whoever gets advanced goes first", which would make the ordering of P7-2 extra turns
     * / P10-4 action advance counter-intuitive and hard to predict.
     */
    @Test
    public void advancingToTheSameActionValueDoesNotJumpAhead() {
        Character a = character("A", 100);
        Character b = character("B", 100);
        Queue q = new Queue(List.of(a, b));

        long seqA = signalOf(q, a).getSequence();
        long seqB = signalOf(q, b).getSequence();
        Assertions.assertTrue(seqA < seqB, "A entered first → smaller sequence number");
        Assertions.assertEquals(a, q.peekNext(), "same action value 150 → A, scheduled earlier, acts first");

        // Both acted once at action value 150 and were both rescheduled to 250 (speed 100 → period 100).
        // ⚠ advanceAction(…, 1e9) MUST NOT be used to "pull someone to the very front" and create the
        // alignment: the clamp would pin them to elapsed, and move() would then drag the clock there —
        // they would act twice in a row.
        Assertions.assertEquals(a, nextActor(q));
        Assertions.assertEquals(b, nextActor(q));
        Assertions.assertEquals(250, signalOf(q, a).getNextActionTime(), EPS);
        Assertions.assertEquals(250, signalOf(q, b).getNextActionTime(), EPS);
        Assertions.assertEquals(150, q.getElapsed(), EPS, "both acted at 150, so the clock stops at 150");

        // Now delay A to 280, then delay B to 280 as well: both have the same value.
        // delayAction is used rather than advanceAction, to avoid scheduling either one onto elapsed.
        // Note the sequence numbers asserted must be the ones **after acting**: both were just
        // rescheduled, so their sequence numbers have been renewed.
        long seqAAfterActing = signalOf(q, a).getSequence();
        long seqBAfterActing = signalOf(q, b).getSequence();
        Assertions.assertTrue(seqAAfterActing < seqBAfterActing,
                "acting at the same moment, A goes first (A was scheduled earlier) → after re-taking sequence numbers A is still smaller");

        q.delayAction(a, 30);
        q.delayAction(b, 30);

        Assertions.assertEquals(280, signalOf(q, a).getNextActionTime(), EPS);
        Assertions.assertEquals(280, signalOf(q, b).getNextActionTime(), EPS);
        Assertions.assertEquals(seqAAfterActing, signalOf(q, a).getSequence(), "an action bar push does not re-take a sequence number");
        Assertions.assertEquals(seqBAfterActing, signalOf(q, b).getSequence(), "an action bar push does not re-take a sequence number");
        Assertions.assertEquals(a, q.peekNext(), "same action value 280 → A, scheduled earlier, acts first");

        Assertions.assertEquals(a, nextActor(q), "A goes first");
        Assertions.assertEquals(b, nextActor(q), "then B");
    }

    /**
     * After an actor is rescheduled it gets a **new** sequence number, so it cannot use its old
     * sequence number to cut in ahead of its tier: the actor goes to the back of the queue and the
     * next one in the same tier takes over.
     */
    @Test
    public void actorGoesToTheBackOfItsTierAfterActing() {
        Character a = character("A", 100);
        Character b = character("B", 100);
        Queue q = new Queue(List.of(a, b));

        long seqBefore = signalOf(q, a).getSequence();
        Assertions.assertEquals(a, nextActor(q));                 // A acts and is rescheduled

        Assertions.assertTrue(signalOf(q, a).getSequence() > seqBefore,
                "after acting it takes a new sequence number (queued at the end of its tier)");
        Assertions.assertEquals(b, q.peekNext(), "B is at 150, A has moved to 250");
        Assertions.assertEquals(b, nextActor(q), "next up is B");
    }

    /**
     * After a death removal, the remaining ones still act in order and are not thrown off by the
     * heap being rebuilt.
     */
    @Test
    public void removalKeepsDeterministicOrder() {
        Character a = character("a", 100);
        Character b = character("b", 100);
        Character c = character("c", 100);
        Queue q = new Queue(List.of(a, b, c));

        Assertions.assertTrue(q.removeCombatant(b));

        Assertions.assertEquals(a, q.peekNext());
        Assertions.assertEquals(a, nextActor(q));
        Assertions.assertEquals(c, nextActor(q), "after b is removed it is c's turn");
    }

    /**
     * When speeds differ (action values differ), the tie-break sequence number must not steal the
     * show: the smaller action value still acts first.
     *
     * <p>⚠ Here only "who acts first" is asserted, **not** which of two signals with the same value
     * comes first — that depends on the two sequence numbers, and after acting the fast one is
     * rescheduled (see {@link #actorGoesToTheBackOfItsTierAfterActing}).
     * What this test proves is: {@code compareTo} always uses the action value as the primary key.
     */
    @Test
    public void sequenceNeverOverridesTheActionValue() {
        Character slow = character("slow", 100);      // first round 150
        Character fast = character("fast", 200);      // first round 75
        Queue q = new Queue(List.of(slow, fast));

        Assertions.assertNotEquals(signalOf(q, slow).getSequence(), signalOf(q, fast).getSequence(),
                "the two signals must get different sequence numbers");
        Assertions.assertEquals(fast, q.peekNext(), "fast's action value 75 < 150, so it acts first");

        Assertions.assertEquals(fast, nextActor(q), "fast goes first");
        Assertions.assertEquals(75, q.getElapsed(), EPS);
    }

    /**
     * A latecomer entering mid-battle (a summon / P9-4) gets a new sequence number, and compared
     * with an old unit at the same instant it is placed behind.
     */
    @Test
    public void latecomerGetsAHigherSequence() {
        Character early = character("early", 100);
        Queue q = new Queue(List.of(early));

        Character late = character("late", 100);
        q.addCombatant(late);

        Assertions.assertTrue(signalOf(q, early).getSequence() < signalOf(q, late).getSequence(),
                "the one that entered earlier has the smaller sequence number");
    }

    // ==================================================================

    /** Builds 8 same-speed units and records the names of the first 8 turns. */
    private static List<String> runEqualSpeedBatch(int count) {
        List<CanHit> characters = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            characters.add(character("c" + i, 100));
        }
        Queue q = new Queue(characters);

        List<String> order = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            order.add(nextActor(q).getName());
        }
        return order;
    }

    /** {@code move()} + {@code setTopZero()}: consumes one turn and returns the actor. */
    private static CanHit nextActor(Queue q) {
        q.move();
        CanHit actor = q.getCurrentActor().getCanHit();
        q.setTopZero();
        return actor;
    }

    private static Character character(String name, int speed) {
        return Character.fromAttributes(name, 10_000, 100, 100, speed);
    }

    private static Signal signalOf(Queue q, CanHit target) {
        List<Signal> matches = q.getHeap().stream()
                .filter(s -> s.getCanHit().equals(target))
                .toList();
        Assertions.assertEquals(1, matches.size(),
                target.getName() + " should appear only once in the action bar, actually " + matches.size() + " times");
        return matches.getFirst();
    }
}
