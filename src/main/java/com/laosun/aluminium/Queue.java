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
            // P7-1：首轮 150，后续每轮 100 → 首个周期 ×1.5
            s.markFirstRound();                  // 顺带把 remaining 置为 1.5 × cycleTime()
            s.setNextActionTime(s.getRemaining());
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
     * <p>{@code elapsed} **只增不减**：即便某个信号因为浮点误差落在当前时钟之前
     * （见 {@link #advanceActionByPercent} 的说明），时钟也不会倒退。
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
        elapsed = Math.max(elapsed, next.getNextActionTime());   // 时钟不倒退
        // P7 修正 E2：把这段时钟推进记到所有人的"周期进度"账本上，
        // 这样中途变速才能按"已经走了几成"重排（见 Signal#refreshSpeed(double)）。
        if (timePassed > 0) {
            for (Signal s : heap) {
                s.advanceProgress(timePassed);
            }
        }
        currentActor = next;
        return timePassed;
    }

    /**
     * Resets the **current actor's** action cycle (see {@link #move()}): its next action is
     * one full cycle from now, and it is re-inserted into the heap.
     *
     * <p><b>为什么要用 currentActor 而不是堆顶</b>：这个方法的名字与含义是"行为结束了，
     * 把**行动者**推回队尾"。堆顶只是"此刻最早的人"，二者在遇到行动条操纵后就不再等价 ——
     * 例如拉条把某人拉到 {@code elapsed} 之后，堆顶会变成那个人，若按堆顶重置，
     * 行动者的周期没重置（他会连动两次），而被重置的是别人。
     *
     * <p>{@code currentActor == null}（没调 {@link #move()} 就调了本方法）时什么都不做：
     * 静默地把堆顶推后一个周期等于"跳过一个人的回合"，那是更坏的失败方式。
     */
    public void setTopZero() {
        Signal acting = currentActor;
        if (acting == null) {
            return;                                  // 没有正在行动的人 → 无事可做（不要动堆顶）
        }
        currentActor = null;
        if (!heap.remove(acting)) {
            return;                                  // 他已经不在队里了（已死被移除）
        }
        acting.refreshSpeed();
        acting.endFirstRound();                      // 首轮系数用完即止
        acting.markActed(elapsed);                   // remaining = cycleTime()，next = elapsed + 周期
        heap.offer(acting);
    }

    public boolean resetSignal(Signal signal) {
        if(signal == null || !heap.contains(signal)) {
            return false;
        }
        heap.remove(signal);
        signal.refreshSpeed();
        signal.endFirstRound();
        signal.markActed(elapsed);
        heap.offer(signal);
        return true;
    }

    /**
     * 速度变化后重排该单位的行动时间（P7 修正 E2）：把他的行动时间按"已积累进度"等比换算。
     *
     * <p>调用方是 {@code Battle.onSpeedChanged}（由 {@code CanHit} 的属性变化回调触发）。
     * 目标不在队里（已死 / 未入场）时返回 {@code false}，不报错。
     *
     * @param target 速度发生变化的单位
     * @return {@code true} = 他的行动时间被重排了
     */
    public boolean refreshSpeed(CanHit target) {
        if (target == null) {
            return false;
        }
        for (Signal signal : heap) {
            if (signal.getCanHit() == target) {
                signal.refreshSpeed(elapsed);
                rebuildHeap();                       // 键改了，堆需要重排
                return true;
            }
        }
        return false;
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
     * <p>⚠ <b>必须 clamp</b>：{@code a - (a-e)·p ≥ e} 在数学上成立，但 binary64 不保证 ——
     * 差一个 ulp 就会让 {@code nextActionTime} **略小于 {@code elapsed}**，
     * 于是 {@link #move()} 会把全局时钟往回拨，接着 {@link #setTopZero()} 就会去重置
     * 那个"落在过去"的单位，导致真正的行动者连动两次。所以这里与 {@link #advanceAction} 一样取 max。
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
