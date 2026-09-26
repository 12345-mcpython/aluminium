package com.laosun.aluminium;

import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Signal;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A turn-based action queue using absolute-time-based turn ordering.
 * <p><b>Thread-safety:</b> This class is NOT thread-safe. It is designed to be used
 * exclusively on the main game/logic thread. External synchronization is required
 * if accessed from multiple threads.
 *
 * <p>Each combatant's {@link Signal} stores a <b>next action time</b> (absolute global time).
 * The queue maintains a global {@link #elapsed} clock and a {@link PriorityQueue} ordered by
 * {@code nextActionTime}. This avoids per-frame O(n) iterations and O(n log n) re-sorts.
 *
 * <p><b>Algorithm:</b>
 * <ul>
 *   <li>{@link #move()} — peeks the smallest {@code nextActionTime}, advances {@code elapsed}
 *   to that point. No iteration needed.</li>
 *   <li>{@link #setTopZero()} — takes the combatant on top of the heap, resets it to
 *   {@code elapsed + cycleTime()}, and re-inserts. O(log n).</li>
 *   <li>{@link #initialize()} — resets elapsed to zero, computes the first
 *   {@code nextActionTime} for all combatants (first round ×1.5). O(n log n).</li>
 *   <li>{@link #addCombatant(CanHit)} — computes {@code nextActionTime = elapsed + cycleTime()},
 *   offers to heap. O(log n).</li>
 * </ul>
 *
 * <p>Derived display properties:
 * <pre>{@code timeRemaining  = max(0, nextActionTime - elapsed)
 * actionLength    = max(0, 10000 - timeRemaining * speed)}</pre>
 *
 * <p>Usage:
 * <pre>{@code
 * Queue q = new Queue(List.of(char1, char2, char3));
 * q.initialize();
 * q.move();         // advance to next combatant's turn
 * q.setTopZero();   // reset the acting combatant's action cycle
 * }</pre>
 */
@Getter
@ToString
public final class Queue {
    private static final double ACTION_THRESHOLD = 10000;
    /**
     * Tiny quantity used for float comparison: attributes an {@code elapsed} that lands exactly
     * on a round boundary to the *previous* round (see {@link #getRound()}).
     */
    private static final double EPSILON = 1e-9;
    /**
     * Heap ordered by {@link Signal#nextActionTime} (ascending), with the E4 scheduling
     * sequence as tie-break so equal action values have a defined order.
     *
     * <p>The comparator is written as an explicit lambda (rather than relying on {@link Signal}'s
     * {@code Comparable} natural order): the intent is clear, and {@link #snapshot()} reuses the
     * exact same comparison rule, so the two can never drift apart.
     */
    private final PriorityQueue<Signal> heap = new PriorityQueue<>(Signal::compareTo);
    /**
     * Global elapsed time since simulation start.
     */
    private double elapsed;
    /**
     * The combatant currently at their action point (set by move(), cleared by setTopZero()).
     * -- GETTER --
     * Returns the Signal that is currently at its action point (set by move()).
     * Null if no one is currently acting.
     */
    private Signal currentActor;
    /**
     * Pending **extra turn** actor (P7-2): the next {@link #move()} is performed by them,
     * and it **does not advance the clock** (so it consumes no action value and does not
     * change the round either).
     *
     * <p>{@code null} = no extra turn. Identity is compared with {@code ==}; {@code CanHit}
     * does not override equals.
     */
    private CanHit extraTurnActor;
    /**
     * When the extra turn was granted, that actor's original {@code nextActionTime} (P7-2).
     *
     * <p>The extra turn is implemented by temporarily pinning their action time to {@code elapsed}
     * (see {@link #grantExtraTurn}); it MUST be handed back afterwards, otherwise one of their
     * **normal** turns gets eaten by this extra turn.
     * What is stored is the snapshot taken at grant time, so that if someone else pushes or pulls
     * their action bar during the extra turn it does not matter.
     */
    private double extraTurnOriginalTime;
    /**
     * The schedule to restore on the next {@link #move()} after the extra turn actor has acted (P7-2).
     *
     * <p>Why not restore it right inside the extra turn: after restoring, the top of the heap is
     * them again, and the next {@code move()} would directly advance their **normal** turn — the
     * extra turn would have been given away for nothing. So it is deferred to the start of the
     * next {@code move()}.
     */
    private ExtraTurnRestore pendingRestore;

    /**
     * Empty initializer
     */
    public Queue() {
    }

    /**
     * Creates a queue with an initial set of combatants, **already initialized**
     * (i.e. the first-round 150 action value multiplier has already been applied, matching
     * how {@code Battle} uses it).
     *
     * @param initialCombatants the starting combatants; must not be empty
     */
    public Queue(List<CanHit> initialCombatants) {
        if (initialCombatants.isEmpty()) {
            throw new IllegalArgumentException("Initial combatants cannot be empty");
        }
        addCombatants(initialCombatants);
        initialize();
    }

    // ─── Combatant management ──────────────────────────────────────────

    /**
     * Returns the combatant that will act next (without advancing time).
     * This is the combatant with the smallest {@code nextActionTime}.
     */
    public CanHit peekNext() {
        Signal s = heap.peek();
        return s != null ? s.getCanHit() : null;
    }

    /**
     * Returns the combatant whose turn it is right now — i.e. the top of
     * the heap with remaining time <= 0. Returns {@code null} if no one
     * is at their action point, or if the queue is empty.
     *
     * <p>Typically called after {@link #move()} to get the acting combatant:</p>
     * <pre>{@code
     * queue.move();
     * CanHit actor = queue.getNext();  // the combatant who acts now
     * }</pre>
     */
    public CanHit getNext() {
        Signal s = heap.peek();
        if (s == null) return null;
        return s.getNextActionTime() <= elapsed ? s.getCanHit() : null;
    }

    /**
     * Returns the remaining time until the next combatant acts.
     */
    public double timeUntilNext() {
        Signal s = heap.peek();
        return s != null ? Math.max(0, s.getNextActionTime() - elapsed) : 0;
    }

    /**
     * Returns the combatant at the given logical queue position.
     * Note: requires snapshotting the heap; use sparingly.
     *
     * @param index queue position (0 = next to act)
     * @return the combatant at that position
     */
    public CanHit getCombatant(int index) {
        return snapshot().get(index).getCanHit();
    }

    /**
     * Returns the number of combatants in the queue.
     */
    public int size() {
        return heap.size();
    }

    /**
     * Adds a single combatant, scheduled one full cycle from the current global time.
     * Duplicates are ignored.
     *
     * <p>⚠ The first-round ×1.5 multiplier is **NOT** applied here (P7-1 only affects
     * {@link #initialize()}): units that join mid-battle (summons, P9-4) are queued at their
     * normal cycle. If the "first round" should later cover mid-battle entries too, change this
     * and update {@code QueueRoundTest} accordingly.
     *
     * @param combatant the combatant to add; null is silently ignored
     */
    public void addCombatant(CanHit combatant) {
        if (combatant == null) {
            return;
        }
        for (Signal s : heap) {
            if (s.getCanHit() == combatant) {
                return;
            }
        }
        Signal sig = new Signal(combatant);
        sig.markScheduled();                 // E4: tie-break sequence number for equal action values
        sig.markActed(elapsed);              // remaining = cycleTime()，next = elapsed + cycleTime()
        heap.offer(sig);
    }

    /**
     * Adds multiple combatants at once.
     */
    public void addCombatants(List<? extends CanHit> combatants) {
        for (CanHit c : combatants) {
            addCombatant(c);
        }
    }

    /**
     * Removes a combatant and its signal from the queue.
     *
     * @param combatant the combatant to remove
     * @return {@code true} if found and removed
     */
    public boolean removeCombatant(CanHit combatant) {
        if (combatant == null) {
            return false;
        }
        return heap.removeIf(s -> s.getCanHit() == combatant);
    }

    // ─── Simulation ────────────────────────────────────────────────────

    /**
     * Resets the simulation: all combatants' action cycles start from time zero,
     * with the **first round stretched to 150 action value** (P7-1).
     */
    public void initialize() {
        elapsed = 0;
        currentActor = null;
        List<Signal> snapshot = new ArrayList<>(heap);
        heap.clear();
        for (Signal s : snapshot) {
            s.refreshSpeed();
            // P7-1: first round 150, every later round 100 → the first cycle is ×1.5
            //
            // E4: the scheduling sequence number is deliberately **not** re-drawn here. The number
            // was already issued by entry order when addCombatant() created the Signal, and what is
            // iterated here is the heap's internal array — its order is determined by the heap
            // structure, so people with different speeds sit in different positions anyway. If the
            // number were re-drawn here, "units with equal speed act in entry order" would no longer
            // hold. initialize()'s only job is to zero the clock and apply the first-round multiplier.
            s.markFirstRound();                  // also sets remaining to 1.5 × cycleTime()
            s.setNextActionTime(s.getRemaining());
            heap.offer(s);
        }
    }

    /**
     * Which round it currently is (P7-1): derived from accumulated action value, {@code first round = 1}.
     *
     * <p>The intervals are **closed on the right**:
     * <pre>
     *   round 1: elapsed ∈ [0, 150]
     *   round 2: elapsed ∈ (150, 250]
     *   round 3: elapsed ∈ (250, 350]   … 100 per round
     * </pre>
     * That is, "the instant a round ends (elapsed lands exactly on the round boundary) still counts
     * as that round" — because action value advances continuously, and {@code elapsed == 150} means
     * the first round has just finished and the next one has not begun.
     * The implementation uses {@code -EPS} to attribute boundary values to the previous round.
     *
     * <p>Good enough for demos/logging; the real round driving (win/loss determination, stage turn
     * limits) is in P7-3.
     *
     * @return the round, starting from 1
     */
    public int getRound() {
        double firstRound = Constant.ROUND_ACTION_VALUE * Constant.FIRST_ROUND_MULTIPLIER;
        if (elapsed <= firstRound) {
            return 1;
        }
        return 2 + (int) ((elapsed - firstRound - EPSILON) / Constant.ROUND_ACTION_VALUE);
    }

    /**
     * Advances time to the next combatant's action.
     *
     * <p>No iteration over all combatants — only advances the global clock.
     * The combatant that acts will have {@code nextActionTime == elapsed}
     * after this call (i.e., zero remaining time).
     *
     * <p>{@code elapsed} **only ever increases**: even if some signal falls before the current
     * clock due to floating-point error (see the note in {@link #advanceActionByPercent}), the
     * clock never goes backwards.
     *
     * @return the amount of time that passed
     */
    public double move() {
        if (heap.isEmpty()) {
            currentActor = null;
            return 0;
        }
        // P7-2: first handle "the pending restore left over from the previous extra turn",
        // then do the normal advance.
        if (pendingRestore != null) {
            applyPendingRestore();
        }
        // P7-2: the extra turn cuts the line. The clock does **not** move, so no action value is
        // consumed and the round does not change either.
        if (extraTurnActor != null) {
            return moveExtraTurn();
        }
        Signal next = heap.peek();
        double timePassed = Math.max(0, next.getNextActionTime() - elapsed);
        elapsed = Math.max(elapsed, next.getNextActionTime());   // the clock never goes backwards
        // P7 fix E2: record this clock advance on everyone's "cycle progress" ledger, so that a
        // mid-flight speed change can be rescheduled by "how far along they already are"
        // (see Signal#refreshSpeed(double)).
        if (timePassed > 0) {
            for (Signal s : heap) {
                s.advanceProgress(timePassed);
            }
        }
        currentActor = next;
        return timePassed;
    }

    /**
     * Consumes one extra turn (P7-2): makes {@link #extraTurnActor} act immediately, with the clock
     * not moving.
     *
     * <p><b>Why their {@code nextActionTime} must be temporarily pinned to {@code elapsed}</b>:
     * the downstream code ({@code Battle.afterMove()}) finishes up based on "{@code currentActor} is
     * the top of the heap" — it calls {@link #setTopZero()} to recompute the actor's cycle from
     * {@code elapsed}. So the line-cutting semantics of an "extra turn" MUST be expressed as "they
     * are now at the front of the queue", otherwise the top of the heap is still someone else and
     * the action bar gets scrambled.
     *
     * <p>Once pinned, it **MUST NOT be handed back here** (handing it back puts them on top of the
     * heap again, and the next {@code move()} would directly advance their normal turn); the restore
     * is deferred to {@link #applyPendingRestore()} at the start of the next {@code move()}.
     *
     * @return always {@code 0}: an extra turn does not advance the clock
     */
    private double moveExtraTurn() {
        CanHit actor = extraTurnActor;
        extraTurnActor = null;

        Signal signal = null;
        for (Signal s : heap) {
            if (s.getCanHit() == actor) {
                signal = s;
                break;
            }
        }
        if (signal == null) {
            // They died / were removed from the queue after receiving the extra turn — this extra turn
            // is void, fall back to a normal advance.
            currentActor = null;
            return move();
        }

        // Temporarily pin to elapsed so the downstream code (Battle.afterMove → setTopZero) can
        // finish up normally on the basis that "they are at the front of the queue".
        // ⚠ The signal **stays in the heap** (only the key is changed + the heap is rebuilt): if it
        // were taken out, setTopZero()'s heap.remove(acting) would fail and the actor would be
        // silently dropped.
        // ⚠ Their original schedule **MUST NOT be handed back here**: doing so would put them back
        // on top of the heap, the next move() would directly advance their normal turn, and the
        // extra turn would effectively not have happened. So it is recorded into pendingRestore and
        // left for the start of the next move() to handle.
        pendingRestore = new ExtraTurnRestore(actor, extraTurnOriginalTime);
        signal.setRemaining(elapsed, 0);
        rebuildHeap();

        currentActor = signal;
        return 0;
    }

    /**
     * The "pending schedule restore" left behind by consuming an extra turn (P7-2).
     */
    private record ExtraTurnRestore(CanHit actor, double originalActionTime) {
    }

    /**
     * Restores the extra-turn actor's schedule to "the value it had when the extra turn was
     * granted" (P7-2).
     *
     * <p>This happens after "the extra turn has been acted out and their cycle has already been
     * rescheduled at normal speed by {@link #setTopZero()}", so this step simply erases that
     * freebie — their normal turn is still waiting for them at its original position.
     */
    private void applyPendingRestore() {
        ExtraTurnRestore restore = pendingRestore;
        pendingRestore = null;
        for (Signal s : heap) {
            if (s.getCanHit() == restore.actor()) {
                s.setRemaining(elapsed, Math.max(0, restore.originalActionTime() - elapsed));
                rebuildHeap();
                return;
            }
        }
        // They are no longer in the queue (dead / removed): there is nothing to restore.
    }

    /**
     * Gives {@code actor} an **extra turn** (P7-2): the next {@link #move()} is performed by them,
     * and it **consumes no action value** (the clock does not move → the round does not change either).
     *
     * <p>Semantic points:
     * <ul>
     *   <li>An extra turn is **NOT** "filling up their action bar". Filling the bar moves their
     *       **normal** turn earlier, whereas an extra turn is a freebie and their normal turn's
     *       schedule stays untouched — so here their action time is temporarily pinned to
     *       {@code elapsed} and restored after they act.</li>
     *   <li>Each {@code grantExtraTurn} takes effect only once; calling it repeatedly on the same
     *       target is equivalent to one call (extra turns do not accumulate).</li>
     *   <li>Target already dead / not in the queue → returns {@code false}, no extra turn.</li>
     *   <li>Only one person can hold an extra turn at a time; granting it to someone else
     *       **replaces** the previous holder.</li>
     * </ul>
     *
     * <p>Typical usage (P5's on-kill talents, e.g. Seele): call it inside {@code afterMove()} —
     * that is, after {@code setTopZero()} — so that the stored "original schedule" is the one from
     * after they acted and got pushed back.
     *
     * @param actor the unit that receives the extra turn
     * @return {@code true} = the extra turn has been scheduled
     */
    public boolean grantExtraTurn(CanHit actor) {
        if (actor == null || actor.isDeath()) {
            return false;
        }
        Signal signal = null;
        for (Signal s : heap) {
            if (s.getCanHit() == actor) {
                signal = s;
                break;
            }
        }
        if (signal == null) {
            return false;                            // not in the queue (not yet entered / removed)
        }
        extraTurnActor = actor;
        extraTurnOriginalTime = signal.getNextActionTime();
        return true;
    }

    /**
     * Whether an extra turn is currently scheduled; if so, returns that actor (P7-2).
     * {@code null} if there is none.
     */
    public CanHit getExtraTurnActor() {
        return extraTurnActor;
    }

    /**
     * Resets the **current actor's** action cycle (see {@link #move()}): its next action is
     * one full cycle from now, and it is re-inserted into the heap.
     *
     * <p><b>Why use currentActor instead of the top of the heap</b>: the name and meaning of this
     * method is "the action is over, push the **actor** back to the end of the queue". The top of
     * the heap is merely "whoever is earliest right now", and the two stop being equivalent once
     * action-bar manipulation is involved — for example, after an advance pulls someone to
     * {@code elapsed}, the top of the heap becomes that person; resetting by heap top would leave
     * the actor's cycle un-reset (they would act twice in a row) while someone else got reset.
     *
     * <p>When {@code currentActor == null} (this method was called without calling {@link #move()}),
     * nothing is done: silently pushing the heap top back by one cycle amounts to "skipping someone's
     * turn", which is a worse way to fail.
     */
    public void setTopZero() {
        Signal acting = currentActor;
        if (acting == null) {
            return;                                  // nobody is acting → nothing to do (do NOT touch the heap top)
        }
        currentActor = null;
        if (!heap.remove(acting)) {
            return;                                  // they are no longer in the queue (died and were removed)
        }
        acting.refreshSpeed();
        acting.endFirstRound();                      // the first-round multiplier applies once only
        acting.markScheduled();                      // E4: re-schedule → new sequence number (queued after equals)
        acting.markActed(elapsed);                   // remaining = cycleTime(), next = elapsed + cycle
        heap.offer(acting);
    }

    public boolean resetSignal(Signal signal) {
        if(signal == null || !heap.contains(signal)) {
            return false;
        }
        heap.remove(signal);
        signal.refreshSpeed();
        signal.endFirstRound();
        signal.markScheduled();              // E4: re-schedule → new sequence number
        signal.markActed(elapsed);
        heap.offer(signal);
        return true;
    }

    /**
     * After a speed change, reschedules that unit's action time (P7 fix E2): their action time is
     * rescaled in proportion to the "progress already accumulated".
     *
     * <p>The caller is {@code Battle.onSpeedChanged} (triggered by {@code CanHit}'s attribute-change
     * callback). If the target is not in the queue (dead / not yet entered) it returns {@code false}
     * rather than raising.
     *
     * @param target the unit whose speed changed
     * @return {@code true} = their action time was rescheduled
     */
    public boolean refreshSpeed(CanHit target) {
        if (target == null) {
            return false;
        }
        for (Signal signal : heap) {
            if (signal.getCanHit() == target) {
                signal.refreshSpeed(elapsed);
                rebuildHeap();                       // the key changed, the heap needs reordering
                return true;
            }
        }
        return false;
    }

    // ─── Action manipulation ─────────────────────────────

    /**
     * Delays the target combatant's next action by the given time value (delay/push-back).
     *
     * <p>Adds {@code delay} to the target's {@code nextActionTime}, pushing
     * their turn further into the future. If the target is currently at the
     * top of the heap and within the delay window, this effectively moves
     * the next turn to another combatant.
     *
     * <p>Cost: O(n log n) due to heap rebuild after key modification.
     *
     * @param target the combatant to delay
     * @param delay  amount of time to push back (must be &ge; 0)
     * @return {@code true} if the target was found and delayed
     */
    public boolean delayAction(CanHit target, double delay) {
        if (target == null || delay < 0) {
            return false;
        }
        for (Signal s : heap) {
            if (s.getCanHit() == target) {
                s.setNextActionTime(s.getNextActionTime() + delay);
                s.setRemaining(elapsed, s.getNextActionTime() - elapsed);
                rebuildHeap();
                return true;
            }
        }
        return false;
    }

    /**
     * Advances the target combatant's next action by the given time value (advance/pull-forward).
     *
     * <p>Subtracts {@code advance} from the target's {@code nextActionTime},
     * pulling their turn closer. The next action time is clamped so it never
     * goes before the current global {@link #elapsed} time (cannot act in the past).
     *
     * <p>A value larger than the remaining time results in an immediate action
     * ({@code nextActionTime == elapsed}), meaning the target will act next.
     *
     * <p>Cost: O(n log n) due to heap rebuild after key modification.
     *
     * @param target  the combatant to advance
     * @param advance amount of time to pull forward (must be &ge; 0)
     * @return {@code true} if the target was found and advanced
     */
    public boolean advanceAction(CanHit target, double advance) {
        if (target == null || advance < 0) {
            return false;
        }
        for (Signal s : heap) {
            if (s.getCanHit() == target) {
                s.setNextActionTime(Math.max(elapsed, s.getNextActionTime() - advance));
                // Same ledger sync as delayAction (L-26): the pull must survive a later speed change too.
                s.setRemaining(elapsed, s.getNextActionTime() - elapsed);
                rebuildHeap();
                return true;
            }
        }
        return false;
    }

    /**
     * Advances the target combatant's next action by a percentage of their
     * remaining time.
     *
     * <p>If {@code percent = 1.0} (100%), the target acts immediately.
     * If {@code percent = 0.5} (50%), half the remaining wait is skipped.
     *
     * <p>⚠ <b>The clamp is mandatory</b>: {@code a - (a-e)·p ≥ e} holds mathematically, but binary64
     * does not guarantee it — being off by one ulp makes {@code nextActionTime} **slightly less than
     * {@code elapsed}**, so {@link #move()} would wind the global clock backwards and then
     * {@link #setTopZero()} would go and reset the unit that "fell into the past", causing the real
     * actor to act twice in a row. Hence max() is taken here just as in {@link #advanceAction}.
     *
     * @param target  the combatant to advance
     * @param percent fraction of remaining time to skip (0.0 ~ 1.0)
     * @return {@code true} if the target was found and advanced
     */
    public boolean advanceActionByPercent(CanHit target, double percent) {
        if (target == null || percent < 0 || percent > 1) {
            return false;
        }
        for (Signal s : heap) {
            if (s.getCanHit() == target) {
                double remaining = Math.max(0, s.getNextActionTime() - elapsed);
                double advance = remaining * percent;
                s.setNextActionTime(Math.max(elapsed, s.getNextActionTime() - advance));
                rebuildHeap();
                return true;
            }
        }
        return false;
    }

    // ─── Derived display values ────────────────────────────────────────

    /**
     * Computes the remaining time until the given signal's next action.
     */
    public double getTimeRemaining(Signal s) {
        return Math.max(0, s.getNextActionTime() - elapsed);
    }

    /**
     * Computes the accumulated action length for the given signal.
     */
    public double getActionLength(Signal s) {
        double remaining = getTimeRemaining(s);
        return Math.max(0, ACTION_THRESHOLD - remaining * s.getSpeed());
    }

    /**
     * Returns a time-ordered snapshot of all signals for display or iteration.
     *
     * <p>The ordering rule is **exactly the same** as {@link Signal#compareTo}: first compare
     * {@code nextActionTime}, and on a tie compare the scheduling sequence number (P7 fix E4). That
     * way the order of {@code snapshot()} is the actual acting order, and "what is displayed differs
     * from who actually acts" cannot happen.
     *
     * <p>Why reuse {@link Signal#compareTo} directly instead of writing a second comparison rule:
     * two rules would drift apart sooner or later, and that is exactly where E4's "display order ≠
     * acting order" came from.
     *
     * <p>O(n log n), for debugging; what is returned is a copy, so modifying it does not affect the
     * action bar.
     */
    public List<Signal> snapshot() {
        List<Signal> list = new ArrayList<>(heap);
        list.sort(Signal::compareTo);
        return list;
    }

    /**
     * Prints the current action queue status to stdout.
     */
    public void printActionQueue() {
        if (heap.isEmpty()) {
            System.out.println("Action queue is empty.");
            return;
        }
        List<Signal> ordered = snapshot();
        System.out.println("=== Action Queue Status (elapsed=" + String.format("%.2f", elapsed) + ") ===");
        for (int i = 0; i < ordered.size(); i++) {
            Signal s = ordered.get(i);
            System.out.printf("[%d] name = %s, time=%.2f, length=%.2f, speed=%.2f%n",
                    i, s.getCanHit().getName(), getTimeRemaining(s), getActionLength(s), s.getSpeed());
        }
        System.out.println("===========================");
    }

    // ─── Internal ──────────────────────────────────────────────────────

    /**
     * Rebuilds the heap after in-place key modifications. O(n log n).
     */
    private void rebuildHeap() {
        List<Signal> snapshot = new ArrayList<>(heap);
        heap.clear();
        heap.addAll(snapshot);
    }
}
