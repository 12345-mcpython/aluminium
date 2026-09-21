package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

/**
 * A combatant's position in the turn-based action queue.
 *
 * <p>Stores the <b>absolute global time</b> of the next action ({@link #nextActionTime})
 * rather than relative remaining time. This enables the {@link com.laosun.aluminium.Queue}
 * to use a {@link java.util.PriorityQueue} ordered by {@code nextActionTime} for O(log n)
 * operations instead of O(n log n) full re-sorts.
 *
 * <p>The remaining time and action length are derived on demand:
 * <pre>{@code timeRemaining = max(0, nextActionTime - elapsed)
 * actionLength  = max(0, 10000 - timeRemaining * speed)}</pre>
 * where {@code elapsed} is the queue's current global clock.
 */
@Getter
@Setter
public final class Signal implements Comparable<Signal>, Cloneable {
    /**
     * The combatant's current speed (should be refreshed when buffs/debuffs change speed).
     */
    public double speed;
    /**
     * Absolute global time when this combatant will act next.
     */
    public double nextActionTime;
    /**
     * The combatant this signal represents.
     */
    private CanHit canHit;
    /**
     * Unique identifier for this signal.
     */
    private int id = 0;
    /**
     * 距离下一个行动点还剩多少**行动值**（P7 修正 E2）。
     *
     * <p>为什么必须单独记账、而且**必须用"距离"而不是"百分比"**：
     * 首轮（P7-1）的周期被拉长到 1.5 倍，于是"当前周期"的分母在首轮和之后是不同的
     * （150 vs 100）。一旦把进度记成百分比，速度变化时就没法换算 —— 百分比乘以新周期
     * 会同时改掉两个东西。而<b>距离是速度无关的</b>：行动条上"还差多少格"不随速度变化，
     * 速度只决定"每单位时间走几格"。所以：
     *
     * <ul>
     *   <li>速度变化 → 距离不变，按新速度把剩余距离换算成时间；</li>
     *   <li>时间推进 → 距离减少，减少量就是推进的时间。</li>
     * </ul>
     *
     * <p>首轮不变式：{@code nextActionTime - elapsed == remaining} 在 {@code markFirstRound()}
     * 时成立（都是 {@code 1.5 × cycleTime()}），且此后一直成立 —— 这正是 E2 修复要保住的东西。
     */
    private double remaining = 0;
    /**
     * 这个信号是否还没走完首轮排期（P7-1）：首轮的一次预约要乘 ×1.5。
     */
    private boolean firstRound = false;

    /**
     * Creates a signal for the given combatant, caching its current speed.
     *
     * @param moveable the combatant to track
     */
    public Signal(CanHit moveable) {
        this.canHit = moveable;
        this.speed = moveable.getAttribute(AttributeType.SPEED).get();
        if (this.speed <= 0) {
            throw new IllegalArgumentException("Speed must be greater than 0.");
        }
    }

    /**
     * Refreshes the cached speed from the underlying combatant.
     * Called when speed-changing buffs or debuffs are applied.
     * IT'S IMPORTANT TO CALL WHEN CHANGING SPEED!!!
     */
    public void refreshSpeed() {
        this.speed = canHit.getAttribute(AttributeType.SPEED).get();
    }

    /**
     * 速度变化后立刻重算行动时间（P7 修正 E2）。
     *
     * <pre>
     *   progress  = clamp(remaining / 旧的一次预约长度, 0, 1)   // 旧预约长度含首轮系数
     *   newLength = 新周期 × (首轮 ? 1.5 : 1)
     *   remaining = progress × newLength
     *   next      = elapsed + remaining
     * </pre>
     *
     * <p>语义：<b>已经走掉的那部分进度不变，没走完的那部分按新速度重算</b>。
     * <ul>
     *   <li>刚行动完（progress = 0）→ 按新速度重排整整一轮；</li>
     *   <li>刚好要行动（progress = 1）→ {@code remaining} 仍是整轮长度，也就是
     *       <b>预约长度不打折</b>：加速不会让人"凭空提前"，只是把等待等比缩短；</li>
     *   <li>中途变速 → 剩余等待按新旧周期等比缩放。</li>
     * </ul>
     *
     * <p>⚠ 分母必须是"**这一次预约原本的长度**"（{@link #nextCycleLength()}，首轮含 1.5），
     * 不能用 {@code cycleTime()}：首轮的预约是 150 而周期是 100，用 100 当分母会算出
     * progress = 1.5，速度一变就会把首轮系数抹成"立刻行动"。
     *
     * @param elapsed 队列的当前全局时钟
     */
    public void refreshSpeed(double elapsed) {
        double oldLength = nextCycleLength();
        double progress = oldLength > 0 ? Math.clamp(remaining / oldLength, 0, 1) : 0;
        refreshSpeed();                              // 先更新 speed，再算新周期
        double newLength = nextCycleLength();
        remaining = progress * newLength;
        nextActionTime = elapsed + remaining;
    }

    /**
     * 时间推进：剩余距离等量减少（{@link com.laosun.aluminium.Queue#move()} 移动时钟时调用）。
     *
     * <p>距离是速度无关的，所以这里**不需要**知道速度、也不需要按首轮换算 ——
     * 推进 75 秒，行动条就前进 75 格。
     *
     * @param delta 本段推进的实际时间
     */
    public void advanceProgress(double delta) {
        remaining -= delta;
    }

    /**
     * 行动点被消费掉：按当前速度重新预约一个完整周期（首轮的话含 ×1.5）。
     *
     * @param elapsed 队列的当前全局时钟
     */
    public void markActed(double elapsed) {
        remaining = nextCycleLength();
        nextActionTime = elapsed + remaining;
    }

    /**
     * 标记这个信号正处于"首轮"（P7-1）：这一次预约要乘 ×1.5。
     *
     * <p>首次预约的长度就是 {@code 1.5 × cycleTime()}，所以距离也从这里起步。
     */
    public void markFirstRound() {
        this.firstRound = true;
        this.remaining = nextCycleLength();
    }

    /**
     * 首轮结束：之后排期不再乘 {@link Constant#FIRST_ROUND_MULTIPLIER}。
     */
    public void endFirstRound() {
        this.firstRound = false;
    }

    /**
     * 这个信号是否还没走完首轮排期（P7-1）。
     */
    public boolean isFirstRound() {
        return firstRound;
    }

    /**
     * 距离下一个行动点还剩多少行动值（速度无关）。
     */
    public double getRemaining() {
        return remaining;
    }

    /**
     * 返回"从当前时刻起排下一次行动"要用多久（首轮含 ×1.5 系数）。
     */
    public double nextCycleLength() {
        return cycleTime() * (firstRound ? Constant.FIRST_ROUND_MULTIPLIER : 1.0);
    }

    /**
     * Returns the time for one full action cycle at the current speed.
     */
    public double cycleTime() {
        return speed > 0 ? 10000.0 / speed : Double.MAX_VALUE;
    }

    @Override
    public int compareTo(@NotNull Signal o) {
        return Double.compare(this.nextActionTime, o.nextActionTime);
    }

    /**
     * Creates a shallow copy of this signal.
     *
     * @return a new signal with identical field values
     * @throws CloneNotSupportedException if cloning is not supported
     */
    public Signal clone() throws CloneNotSupportedException {
        return (Signal) super.clone();
    }
}
