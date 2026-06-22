package com.laosun.aluminium;

import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Signal;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Comparator;
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
 *   <li>{@link #setTopZero()} — removes the first combatant, advances its
 *   {@code nextActionTime += 10000 / speed}, and re-inserts. O(log n).</li>
 *   <li>{@link #initialize()} — resets elapsed to zero, computes initial {@code nextActionTime}
 *   for all combatants. O(n log n).</li>
 *   <li>{@link #addCombatant(CanHit)} — computes {@code nextActionTime = elapsed + 10000 / speed},
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
     * Global elapsed time since simulation start.
     */
    private double elapsed;

    /** The combatant currently at their action point (set by move(), cleared by setTopZero()). */
    private Signal currentActor;

    /**
     * Heap ordered by {@link Signal#nextActionTime} (ascending).
     */
    private final PriorityQueue<Signal> heap = new PriorityQueue<>();

    /**
     * Creates a queue with an initial set of combatants.
     *
     * @param initialCombatants the starting combatants, may be empty
     */
    public Queue(List<CanHit> initialCombatants) {
        addCombatants(initialCombatants);
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
     * Adds a single combatant. Starts its cycle at the current global time.
     * Duplicates are ignored.
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
        sig.setNextActionTime(elapsed + sig.cycleTime());
        heap.offer(sig);
    }

    /**
     * Adds multiple combatants at once.
     */
    public void addCombatants(List<CanHit> combatants) {
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
     * Resets the simulation: all combatants' action cycles start from time zero.
     */
    public void initialize() {
        elapsed = 0;
        currentActor = null;
        List<Signal> snapshot = new ArrayList<>(heap);
        heap.clear();
        for (Signal s : snapshot) {
            s.refreshSpeed();
            s.setNextActionTime(s.cycleTime());
            heap.offer(s);
        }
    }

    /**
     * Advances time to the next combatant's action.
     *
     * <p>No iteration over all combatants — only advances the global clock.
     * The combatant that acts will have {@code nextActionTime == elapsed}
     * after this call (i.e., zero remaining time).
     *
     * @return the amount of time that passed
     */
    public double move() {
        if (heap.isEmpty()) {
            currentActor = null;
            return 0;
        }
        Signal next = heap.peek();
        double timePassed = Math.max(0, next.getNextActionTime() - elapsed);
        elapsed = next.getNextActionTime();
        currentActor = next;
        return timePassed;
    }

    /**
     * Resets the current (first) combatant's action cycle: removes from top,
     * advances their next action time by one full cycle, and re-inserts.
     *
     * <p>O(log n): one {@code poll()} + one {@code offer()}.
     */
    public void setTopZero() {
        if (heap.isEmpty()) {
            currentActor = null;
            return;
        }
        Signal acting = heap.peek();
        heap.remove(acting);
        acting.refreshSpeed();
        acting.setNextActionTime(elapsed + acting.cycleTime());
        heap.offer(acting);
        currentActor = null;
    }

    /**
     * Returns the Signal that is currently at its action point (set by move()).
     * Null if no one is currently acting.
     */
    public Signal getCurrentActor() {
        return currentActor;
    }

    // ─── Action manipulation ─────────────────────────────

    /**
     * Delays the target combatant's next action by the given time value (推条).
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
                rebuildHeap();
                return true;
            }
        }
        return false;
    }

    /**
     * Advances the target combatant's next action by the given time value (拉条).
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
                s.setNextActionTime(s.getNextActionTime() - advance);
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
     * O(n log n) — use sparingly (debug only).
     */
    public List<Signal> snapshot() {
        List<Signal> list = new ArrayList<>(heap);
        list.sort(Comparator.comparingDouble(Signal::getNextActionTime));
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
        for (Signal s : snapshot) {
            heap.offer(s);
        }
    }
}
