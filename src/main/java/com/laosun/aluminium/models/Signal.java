package com.laosun.aluminium.models;

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
     * The combatant this signal represents.
     */
    private CanHit canHit;
    /**
     * The combatant's current speed (should be refreshed when buffs/debuffs change speed).
     */
    public double speed;
    /**
     * Absolute global time when this combatant will act next.
     */
    public double nextActionTime;
    /**
     * Unique identifier for this signal.
     */
    private int id = 0;

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
