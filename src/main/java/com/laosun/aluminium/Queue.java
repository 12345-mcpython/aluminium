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
     * 一轮的行动值（P7-1）：后续每轮 100。
     */
    private static final double ROUND_ACTION_VALUE = 100;
    /**
     * 首轮行动值倍率（P7-1）：首轮总行动值 **150**，之后每轮 **100**。
     *
     * <p>所以 <b>速度 100 的单位首轮要等 150 才动，第二圈起每 100 动一次</b>；
     * 速度 200 的单位首轮等 75。这不是"首轮整体延后"，而是每个单位的**第一个周期**被拉长 1.5 倍
     * —— 所以首轮里高速单位能多动一次（速度 240 在 150 之内能动两次）。
     *
     * <p>注意：只有 {@link #initialize()} 施加这个系数（战斗开场）；
     * {@link #setTopZero()} / {@link #addCombatant} 之后都按正常周期排队。
     */
    private static final double FIRST_ROUND_MULTIPLIER = 1.5;
    /**
     * 浮点比较用的极小量：把"正好落在轮末"的 elapsed 归到上一轮（见 {@link #getRound()}）。
     */
    private static final double EPSILON = 1e-9;
    /**
     * Heap ordered by {@link Signal#nextActionTime} (ascending).
     */
    private final PriorityQueue<Signal> heap = new PriorityQueue<>();
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
     * Empty initializer
     */
    public Queue() {
    }

    /**
     * Creates a queue with an initial set of combatants, **already initialized**
     * （即首轮 150 行动值的系数已经施加，与 {@code Battle} 的用法一致）。
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
     * <p>⚠ 这里**不施加**首轮 1.5 系数（P7-1 只作用于 {@link #initialize()}）：
     * 中途入场的单位（召唤物、P9-4）按正常周期排队。若将来要让"首轮"也覆盖中途入场，
     * 改这里并同步改 {@code QueueRoundTest}。
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
            // P7-1：首轮 150，后续每轮 100 → 首个周期 ×1.5
            s.setNextActionTime(s.cycleTime() * FIRST_ROUND_MULTIPLIER);
            heap.offer(s);
        }
    }

    /**
     * 当前是第几轮（P7-1）：按累计行动值推算，{@code 首轮 = 1}。
     *
     * <p>区间的口径是"**闭右端**"：
     * <pre>
     *   第 1 轮：elapsed ∈ [0, 150]
     *   第 2 轮：elapsed ∈ (150, 250]
     *   第 3 轮：elapsed ∈ (250, 350]   … 每轮 100
     * </pre>
     * 也就是"某一轮结束的那一刻（elapsed 正好落在轮末）仍算这一轮"——
     * 因为行动值是连续推进的，{@code elapsed == 150} 表示首轮刚走完，下一轮还没开始。
     * 实现上用 {@code -EPS} 把落在边界上的值归到上一轮。
     *
     * <p>够演示/日志用；真正的轮次驱动（胜负判定、关卡回合上限）在 P7-3。
     *
     * @return 轮次，从 1 开始
     */
    public int getRound() {
        double firstRound = ROUND_ACTION_VALUE * FIRST_ROUND_MULTIPLIER;
        if (elapsed <= firstRound) {
            return 1;
        }
        return 2 + (int) ((elapsed - firstRound - EPSILON) / ROUND_ACTION_VALUE);
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

    public void resetSignal(Signal signal) {
        if(signal == null || !heap.contains(signal)) {
            return;
        }
        heap.remove(signal);
        signal.refreshSpeed();
        signal.setNextActionTime(elapsed + signal.cycleTime());
        heap.offer(signal);
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
        heap.addAll(snapshot);
    }
}
