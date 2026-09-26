package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

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
     * The **scheduling sequence number** used to break equal action values (P7 fix E4): the smaller it is, the
     * earlier the combatant acts.
     *
     * <p>Why it is needed: when {@link #nextActionTime} ties, the order of a {@code PriorityQueue} is **undefined**
     * (it only guarantees the heap top is the smallest element, not the relative order of equal elements). So "which
     * of two equal-speed units acts first" turns into a coin flip, and since
     * {@link com.laosun.aluminium.Queue#snapshot()} does a stable sort over the heap array —
     * <b>the displayed order may not equal the actual acting order</b>.
     *
     * <p>The tie-break semantics: "**whoever was enqueued first acts first**": {@link #markScheduled()} takes a
     * globally increasing number on every "scheduling" (creating a number on entry / re-booking after acting /
     * {@code resetSignal}).
     *
     * <p>⚠ Action bar manipulation (push-back / pull-forward / proportional pull-forward) does **not** take a new
     * number, it only changes {@link #nextActionTime}: counting those as "re-scheduling" would produce the
     * counter-intuitive result that "whoever was just pulled forward gets the initiative".
     * So when A is pulled to the same instant as B, B still acts first (B was scheduled earlier).
     */
    private long sequence;
    /**
     * The globally increasing scheduling-sequence number generator.
     *
     * <p>{@code static} is deliberate: sequence numbers only need to be comparable **within** the same battle,
     * and many battles may run back to back in the same JVM (in tests especially), so sharing one generator is the
     * simplest approach and the least likely to go wrong.
     */
    private static final AtomicLong SEQUENCE_GENERATOR = new AtomicLong();
    /**
     * How much **action value** is left until the next action point (P7 fix E2).
     *
     * <p>Why it has to be tracked separately, and **must be a "distance" rather than a "percentage"**:
     * the first round (P7-1) stretches the cycle to 1.5×, so the denominator of the "current cycle" differs
     * between the first round and the rounds after it (150 vs 100). Once progress is recorded as a percentage it
     * can no longer be converted when the speed changes — a percentage times the new cycle would change two things
     * at once. A <b>distance, however, is speed-independent</b>: "how many squares are left" on the action bar does
     * not change with speed, speed only decides "how many squares are covered per unit of time". Therefore:
     *
     * <ul>
     *   <li>speed change → the distance stays, and the remaining distance is converted to time at the new speed;</li>
     *   <li>time advance → the distance shrinks, by exactly the amount of time advanced.</li>
     * </ul>
     *
     * <p>First-round invariant: {@code nextActionTime - elapsed == remaining} holds when
     * {@code markFirstRound()} is called (both are {@code 1.5 × cycleTime()}), and keeps holding from then on —
     * this is exactly what the E2 fix has to preserve.
     */
    private double remaining = 0;
    /**
     * Whether this signal has not yet finished its first-round scheduling (P7-1): the first-round booking is
     * multiplied by ×1.5.
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
     * Recomputes the action time immediately after a speed change (P7 fix E2).
     *
     * <pre>
     *   progress  = max(remaining / old booking length, 0)   // the old booking length includes the first-round factor
     *   newLength = new cycle × (first round ? 1.5 : 1)
     *   remaining = progress × newLength
     *   next      = elapsed + remaining
     * </pre>
     *
     * <p>Semantics: <b>the progress already travelled stays unchanged, and the part not yet travelled is recomputed
     * at the new speed</b>.
     * <ul>
     *   <li>just acted (progress = 0) → the whole round is re-booked at the new speed;</li>
     *   <li>about to act (progress = 1) → {@code remaining} is still the full round length, i.e.
     *       <b>the booking length is not discounted</b>: a speed boost does not let someone "skip ahead out of
     *       thin air", it only shortens the wait proportionally;</li>
     *   <li>speed change halfway through → the remaining wait is scaled in proportion to the old and new cycles;</li>
     *   <li><b>pushed back beyond one booking (progress &gt; 1)</b> → the excess is preserved rather than capped
     *       (L-26). Deliberately <b>not</b> clamped from above: a delayed unit really is more than a round away,
     *       and clamping truncated every push to one full round.</li>
     * </ul>
     *
     * <p>⚠ The denominator must be "**the length this booking originally had**" ({@link #nextCycleLength()},
     * which includes the 1.5 of the first round), and must not be {@code cycleTime()}: in the first round the
     * booking is 150 while the cycle is 100, so using 100 as the denominator yields progress = 1.5, and one speed
     * change would erase the first-round factor into "act immediately".
     *
     * @param elapsed the queue's current global clock
     */
    public void refreshSpeed(double elapsed) {
        double oldLength = nextCycleLength();
        // ⚠ No UPPER clamp (L-26). A unit that has been pushed back by an action delay is legitimately
        // MORE than one booking away, i.e. `remaining > oldLength`; capping the progress at 1 then
        // rewrote it to exactly one full booking, silently truncating the push. That is why a slowed,
        // delayed unit used to be indistinguishable from a merely slowed one.
        // The lower clamp stays — the booking must never go negative.
        double progress = oldLength > 0 ? Math.max(0, remaining / oldLength) : 0;
        refreshSpeed();                              // update speed first, then compute the new cycle
        double newLength = nextCycleLength();
        remaining = progress * newLength;
        nextActionTime = elapsed + remaining;
    }

    /**
     * Time advance: the remaining distance shrinks by the same amount (called when
     * {@link com.laosun.aluminium.Queue#move()} moves the clock).
     *
     * <p>The distance is speed-independent, so this method does **not** need to know the speed, nor does it need a
     * first-round conversion — advance 75 seconds and the action bar moves forward 75 squares.
     *
     * @param delta the actual time advanced by this step
     */
    public void advanceProgress(double delta) {
        remaining -= delta;
    }

    /**
     * The action point has been consumed: book a whole new cycle at the current speed (×1.5 in the first round).
     *
     * @param elapsed the queue's current global clock
     */
    public void markActed(double elapsed) {
        remaining = nextCycleLength();
        nextActionTime = elapsed + remaining;
    }

    /**
     * Marks this signal as being in its "first round" (P7-1): this booking is multiplied by ×1.5.
     *
     * <p>The length of the first booking is exactly {@code 1.5 × cycleTime()}, so the distance starts from there.
     */
    public void markFirstRound() {
        this.firstRound = true;
        this.remaining = nextCycleLength();
    }

    /**
     * The first round is over: later bookings no longer multiply by {@link Constant#FIRST_ROUND_MULTIPLIER}.
     */
    public void endFirstRound() {
        this.firstRound = false;
    }

    /**
     * Directly sets "how much action value is left until the action point" and synchronises
     * {@link #nextActionTime} (P7-2).
     *
     * <p>{@code remaining} and {@code nextActionTime} are two ledgers of the same state
     * (see {@code engine.md} §5.6), so changing one of them means the other must be synchronised —
     * this method exists precisely so that callers do not have to guarantee that themselves.
     *
     * <p>The only caller at the moment is {@link com.laosun.aluminium.Queue#grantExtraTurn}:
     * an extra turn temporarily pins the actor's action time onto {@code elapsed}, and restores it through here
     * afterwards, so that "an extra turn does not consume action value".
     *
     * @param elapsed   the queue's current global clock
     * @param remaining the remaining action value ({@code >= 0})
     */
    public void setRemaining(double elapsed, double remaining) {
        this.remaining = Math.max(0, remaining);
        this.nextActionTime = elapsed + this.remaining;
    }

    /**
     * Returns how long "scheduling the next action from the current instant" takes (×1.5 factor in the first round).
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
        int byTime = Double.compare(this.nextActionTime, o.nextActionTime);
        if (byTime != 0) {
            return byTime;
        }
        return Long.compare(this.sequence, o.sequence);     // E4: equal action value → whoever was scheduled first acts first
    }

    /**
     * Takes a new scheduling sequence number (P7 fix E4). Called by {@link com.laosun.aluminium.Queue} when
     * "this signal is (re)scheduled": creating a Signal and enqueueing it ({@code addCombatant()}),
     * {@code setTopZero()} after acting, and {@code resetSignal()}.
     *
     * <p>⚠ Two places must **not** call it:
     * <ul>
     *   <li>push-back / pull-forward / proportional pull-forward — those only change the action value. If a
     *       pull-forward also took a new number, it would become the counter-intuitive result "whoever was just
     *       pulled forward gets the initiative";</li>
     *   <li>{@code initialize()} — it iterates over the heap's **internal array**, whose order is decided by the
     *       heap structure, and taking numbers there would break "equal-speed units act in entry order".</li>
     * </ul>
     */
    public void markScheduled() {
        this.sequence = SEQUENCE_GENERATOR.getAndIncrement();
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
