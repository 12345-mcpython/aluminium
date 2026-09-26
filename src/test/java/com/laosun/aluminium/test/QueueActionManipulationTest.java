package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.buffs.SpeedBoostBuff;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Three fixes to the action bar (P7 prerequisites):
 * <ol>
 *   <li><b>E1</b>: {@code setTopZero()} resets {@code currentActor}, not the heap top — so it does
 *       not misalign the timeline when someone else is pulled to the very front during an action;
 *       when {@code currentActor == null} it does nothing.</li>
 *   <li><b>E2</b>: a speed change **immediately** re-schedules the action time (converted by the
 *       progress already accumulated) instead of waiting for the next cycle scheduling.</li>
 *   <li><b>E3</b>: {@code advanceActionByPercent} clamps and {@code move()} guards the clock against
 *       going backwards — otherwise the actor would act twice in a row.</li>
 * </ol>
 */
public class QueueActionManipulationTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // E1: setTopZero knows who the actor is
    // ==================================================================

    /**
     * "Pull someone else to the very front" during an action (simulating P7-2 extra turns / P10-4
     * action advance): at that point the heap top is no longer the actor, and {@code setTopZero()}
     * MUST still reset the **actor**.
     *
     * <p>Behaviour before the fix: it reset the heap top (= B, who was pulled up), A's cycle was not
     * reset → A would act twice in a row.
     *
     * <p>⚠ Here A's and B's times are deliberately made **unequal** (250 vs 187.5): the in-heap order
     * of equal keys is undefined (see P7 fix E4, which is not fixed yet), so asserting "who is the
     * heap top" would be a coin flip. The "times happen to be equal" case is covered by
     * {@link #actorIsNotSkippedWhenAnotherSignalSitsInThePast}, which only asserts that the actor was
     * reset and does not assert the heap top's identity.
     */
    @Test
    public void setTopZeroResetsTheActorEvenWhenSomeoneElseWasPulledAhead() {
        Character a = character("A", 100);               // acts first (150)
        Character b = character("B", 80);                // cycle 125 → first round 187.5
        Queue q = new Queue(List.of(a, b));

        q.move();                                        // A acts, elapsed = 150
        Assertions.assertEquals(a, q.getCurrentActor().getCanHit());

        // push A back (action delay): A 250, B 187.5 → the heap top becomes B, while "the one acting" is still A.
        q.delayAction(a, 100);
        Assertions.assertEquals(187.5, signalOf(q, b).getNextActionTime(), EPS);
        Assertions.assertEquals(b, q.peekNext(), "the heap top is now B, not the actor A");

        q.setTopZero();                                  // end A's turn

        Assertions.assertEquals(250, signalOf(q, a).getNextActionTime(), EPS,
                "A (the actor) had its cycle reset: elapsed + 100");
        Assertions.assertEquals(187.5, signalOf(q, b).getNextActionTime(), EPS,
                "B is unaffected (it is still 187.5, and it is next to act)");
        Assertions.assertNull(q.getCurrentActor(), "the actor has been cleared");

        q.move();
        Assertions.assertEquals(b, q.getCurrentActor().getCanHit(), "B acts next");
        Assertions.assertEquals(187.5, q.getElapsed(), EPS);
    }

    /**
     * Calling {@code setTopZero()} without having called {@code move()}: nothing happens.
     *
     * <p>Before the fix it would silently push the heap top back by a whole cycle — equivalent to
     * "skipping someone's turn", which is the worse failure mode.
     */
    @Test
    public void setTopZeroWithoutAMoveDoesNothing() {
        Character a = character("A", 100);
        Queue q = new Queue(List.of(a));
        double before = signalOf(q, a).getNextActionTime();

        q.setTopZero();

        Assertions.assertEquals(before, signalOf(q, a).getNextActionTime(), EPS,
                "there is no acting unit → not one byte should change");
    }

    // ==================================================================
    // E2: a speed change re-schedules immediately
    // ==================================================================

    /**
     * Speed boost mid-battle: the remaining wait is converted by the **progress already accumulated**.
     *
     * <p>Speed 100 → cycle 100, first round next = 150 (progress ledger = 1/1.5 = 2/3).
     * The enemy in the team has 132 speed (first round 113.64) and acts first; after running
     * {@code move()} twice, elapsed = 150 and hero has just finished acting (next = 250, progress
     * back to 0).
     *
     * <p>Now double the speed to 200 (cycle 50): the new next = elapsed + (1 - 0) × 50 = 200.
     */
    @Test
    public void speedChangeReSchedulesTheSignalImmediately() {
        Character hero = character("hero", 100);
        Battle battle = new Battle(List.of(hero), List.of(dummy()), new Random(0));
        Queue q = battle.queue;

        // enemy at 132 speed → first round 10000/132 × 1.5; earlier than hero's 150, so it acts first
        Assertions.assertEquals(10000.0 / 132 * 1.5, signalOf(q, dummyOf(battle)).getNextActionTime(), 1e-6);

        q.move();                                        // the enemy acts
        Assertions.assertNotEquals(hero, q.getCurrentActor().getCanHit());
        q.setTopZero();
        q.move();                                        // now it is hero's turn, elapsed = 150
        Assertions.assertEquals(hero, q.getCurrentActor().getCanHit());
        q.setTopZero();                                  // hero → next = 250

        Signal heroSignal = signalOf(q, hero);
        Assertions.assertEquals(250, heroSignal.getNextActionTime(), EPS);

        hero.getBuffManager().addBuff(new SpeedBoostBuff(2, 1.0));   // speed 100 → 200

        Assertions.assertEquals(200, heroSignal.getNextActionTime(), EPS,
                "immediately re-scheduled: 150 + (1 - 0) × 50 = 200 (instead of waiting for the next cycle scheduling)");
        Assertions.assertEquals(200, hero.getAttribute(AttributeType.SPEED).get(), EPS,
                "the stat sheet really did become 200");
    }

    /**
     * Removing the speed boost likewise falls back to the original cycle immediately.
     *
     * <p>hero at speed 200 → first round 75, earlier than the 132-speed enemy's 113.64, so this time
     * hero really does act first.
     */
    @Test
    public void removingTheSpeedBuffReSchedulesBack() {
        Character hero = character("hero", 100);
        Battle battle = new Battle(List.of(hero), List.of(dummy()), new Random(0));
        Queue q = battle.queue;

        SpeedBoostBuff buff = new SpeedBoostBuff(2, 1.0);
        hero.getBuffManager().addBuff(buff);             // hero speed 200 → cycle 50, first round 75
        q.move();                                        // hero acts first (75 < enemy's 113.64)
        Assertions.assertEquals(hero, q.getCurrentActor().getCanHit());
        q.setTopZero();                                  // hero → 75 + 50 = 125

        Assertions.assertEquals(125, signalOf(q, hero).getNextActionTime(), EPS);

        hero.getBuffManager().removeBuff(buff);          // speed back to 100

        // just acted (progress 0) → new next = 75 + (1 - 0) × 100 = 175
        Assertions.assertEquals(175, signalOf(q, hero).getNextActionTime(), EPS,
                "removing the speed boost also takes effect immediately");
    }

    /**
     * Changing the attribute directly (not through a buff) also triggers the re-scheduling — the
     * trigger point is {@code CanHit.setAttribute}.
     *
     * <p>What this asserts is that the **first-round coefficient is preserved**: a unit at speed 100
     * has a first-round reservation length of {@code 100 × 1.5 = 150}, i.e. 150 squares left on the
     * action bar. After changing the speed to 200 (cycle 50), those remaining 150 squares are walked
     * at the new speed: {@code 150 / 200 × 10000 = 75}.
     *
     * <p>A wrong implementation computes something else: using {@code cycleTime()} (100) as the
     * denominator to back out the progress gives 1.5, which clamps to 1 and becomes "act immediately"
     * (0); simply dropping the first-round coefficient gives 50.
     */
    @Test
    public void settingTheSpeedAttributeDirectlyAlsoReSchedules() {
        Character hero = character("hero", 100);
        Battle battle = new Battle(List.of(hero), List.of(dummy()), new Random(0));
        Queue q = battle.queue;
        Signal signal = signalOf(q, hero);

        Assertions.assertEquals(150, signal.getNextActionTime(), EPS, "before the change: first round 150");
        Assertions.assertEquals(150, signal.getRemaining(), EPS, "there are still 150 squares left on the action bar");

        hero.setAttribute(AttributeType.SPEED, new DoubleValue(200));

        Assertions.assertEquals(75, signal.getNextActionTime(), EPS,
                "the remaining 150 squares are walked at speed 200: 150 / 200 × 10000 = 75");
    }

    // ==================================================================
    // E3: clamp, the actor does not act twice in a row
    // ==================================================================

    /**
     * {@code advanceActionByPercent(…, 1.0)} pushes the action time exactly down to {@code elapsed}:
     * the clock does not go backwards, and the actor is not repeatedly consumed by {@code move()}
     * for "lying in the past".
     */
    @Test
    public void advanceByPercentNeverGoesBelowElapsed() {
        Character a = character("A", 100);
        Queue q = new Queue(List.of(a));

        q.move();                                        // elapsed = 150
        q.setTopZero();                                  // A → 250
        q.advanceActionByPercent(a, 1.0);                // 100%: should land exactly on elapsed

        Assertions.assertEquals(150, signalOf(q, a).getNextActionTime(), EPS, "clamped to elapsed");

        Assertions.assertEquals(0, q.move(), EPS, "the clock is already at 150, so it neither goes back nor forward");
        Assertions.assertEquals(a, q.getCurrentActor().getCanHit(), "A acts again immediately");
        Assertions.assertEquals(150, q.getElapsed(), EPS, "the clock does not go backwards");
    }

    /**
     * The **necessity** of the clamp: when a signal that already lies before {@code elapsed} (even
     * by a single ulp) is advanced by a percentage again, {@code remaining} MUST be maxed to 0 —
     * otherwise the "difference" is negative and the advance would push it **further into the past**,
     * and {@link Queue#move()} would then wind the global clock backwards.
     *
     * <p>This is not hypothetical: in binary64 {@code a - (a-e)·p ≥ e} holds mathematically but is
     * not guaranteed in floating point, and action-bar manipulation (P10-4 action advance / P7-2
     * extra turn) already schedules signals onto {@code elapsed}, so stacking one more advance on top
     * lands right here.
     *
     * <p>How it is constructed: take the signal reference out of the heap (the {@code Queue}'s
     * "remaining distance" ledger is only synchronised on move/setTopZero/refreshSpeed, so changing
     * {@code nextActionTime} directly does not break the logic this test verifies —
     * {@code advanceActionByPercent} only reads {@code nextActionTime}).
     */
    @Test
    public void advanceByPercentClampsASignalThatIsAlreadyInThePast() {
        Character a = character("A", 100);
        Queue q = new Queue(List.of(a));
        Signal signal = signalOf(q, a);

        signal.setNextActionTime(q.getElapsed() - 1e-7);   // already lies in the past

        q.advanceActionByPercent(a, 0.5);

        Assertions.assertEquals(0, signal.getNextActionTime(), EPS,
                "clamped to elapsed(0), instead of being pushed further into the past");

        Assertions.assertEquals(0, q.move(), EPS, "the clock does not go backwards");
        Assertions.assertEquals(a, q.getCurrentActor().getCanHit(), "it acts immediately");
    }

    /**
     * The core assertion: after pulling **another person** onto the action point (the same value as
     * {@code elapsed}), {@code setTopZero()} still resets only the one that just acted, and it does
     * not act twice.
     *
     * <p>At this point both units' {@code nextActionTime} are 150 — the in-heap order of equal keys
     * is undefined (P7 fix E4), so only "the actor was reset" and "the clock does not go backwards"
     * are asserted here, and the heap top's identity is **not**.
     */
    @Test
    public void actorIsNotSkippedWhenAnotherSignalSitsInThePast() {
        Character a = character("A", 100);
        Character b = character("B", 100);
        Queue q = new Queue(List.of(a, b));

        q.move();                                        // A acts, elapsed = 150
        q.advanceAction(b, 1000);                        // B is pulled to 150 (clamped to elapsed, the same value as A)

        Assertions.assertEquals(150, signalOf(q, b).getNextActionTime(), EPS);

        q.setTopZero();                                  // end A's turn (the one reset MUST be A)

        Assertions.assertEquals(250, signalOf(q, a).getNextActionTime(), EPS,
                "A is pushed back to 250 (its next turn comes next round)");
        Assertions.assertEquals(150, signalOf(q, b).getNextActionTime(), EPS,
                "B is still at 150 (it was not reset as if it were the actor)");

        q.move();
        Assertions.assertEquals(b, q.getCurrentActor().getCanHit(), "B acts next (not A acting twice)");
        Assertions.assertEquals(150, q.getElapsed(), EPS, "the clock did not go backwards");
    }

    // ==================================================================

    /**
     * L-26: a pending push must survive a speed change.
     *
     * <p>{@code remaining} and {@code nextActionTime} are two ledgers of one state, and
     * {@code delayAction} only ever wrote the second one — so the next {@code refreshSpeed} recomputed
     * the booking from the **stale** {@code remaining} and threw the push away. Measured before the
     * fix: a Quantum break's extra delay had literally no observable effect (28.409 with and without
     * it), which is how this was found.
     *
     * <p>Both units take the **same** speed change, so nothing but the push can separate them — which
     * makes this test fail unless <b>both</b> halves are fixed:
     * <ol>
     *   <li>{@code delayAction} synchronises {@code remaining} (otherwise the push is already lost
     *       from the ledger);</li>
     *   <li>{@code refreshSpeed} stops clamping progress at 1 (a pushed unit is legitimately
     *       <b>more than one cycle</b> away; capping it there silently truncates the push to a full
     *       round, which is exactly what made the two bookings equal).</li>
     * </ol>
     */
    @Test
    public void aDelaySurvivesASpeedChange() {
        Character a = character("A", 100);
        Character b = character("B", 100);
        Queue q = new Queue(List.of(a, b));              // 150 each; the tie is broken by scheduling sequence

        q.delayAction(b, 50);                            // b → 200
        Assertions.assertEquals(200, signalOf(q, b).getNextActionTime(), EPS,
                "precondition: the push landed on the action time");

        slowDown(q, a);                                  // the same change on both, so only the push matters
        slowDown(q, b);

        Assertions.assertTrue(signalOf(q, b).getNextActionTime() > signalOf(q, a).getNextActionTime(),
                "the push must survive the speed change, but both were re-booked to "
                        + signalOf(q, a).getNextActionTime() + " — the delay was discarded");
        Assertions.assertSame(a, q.peekNext(), "so A still acts first");
    }

    /** Applies a real speed change the way the engine does: rewrite the attribute, then reschedule. */
    private static void slowDown(Queue q, Character target) {
        target.setAttribute(AttributeType.SPEED, new DoubleValue(80));
        q.refreshSpeed(target);
    }

    private static Character character(String name, int speed) {
        return Character.fromAttributes(name, 10_000, 100, 100, speed);
    }

    private static Enemy dummy() {
        return EnemyFactory.create(1002011, 90, 1);
    }

    /** The enemy in the team (Ice Edge 冰锋, 132 speed): first round {@code 10000/132 × 1.5 ≈ 113.64}, so it acts before a speed-100 character (150). */
    private static CanHit dummyOf(Battle battle) {
        return battle.enemies.getFirst();
    }

    /**
     * Finds a signal in the heap by the {@code CanHit}'s **identity**.
     *
     * <p>⚠ You MUST NOT write it as {@code getHeap().stream().findFirst()}: the iteration order of a
     * {@code PriorityQueue} is the **heap-array order**, not time order, and it is not guaranteed to
     * match insertion order either — two units of equal speed will swap results (my first version got
     * it wrong exactly this way). Here it filters with {@code equals} ({@code CanHit} does not
     * override it → identity comparison) and then asserts uniqueness.
     */
    private static Signal signalOf(Queue q, CanHit target) {
        List<Signal> matches = q.getHeap().stream()
                .filter(s -> s.getCanHit().equals(target))
                .toList();
        Assertions.assertEquals(1, matches.size(),
                target.getName() + " should appear exactly once in the action bar, but appears " + matches.size() + " times");
        return matches.getFirst();
    }
}
