package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.AttributeType;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

/**
 * A combatant's position in the turn-based action queue.
 *
 * <p>Each signal tracks the entity's speed, accumulated action length, and
 * predicted time until the next turn. Time is calculated as
 * {@code (ACTION_THRESHOLD - length) / speed}, where the threshold is {@code 10000}.
 */
@Getter
@Setter
public final class Signal implements Comparable<Signal>, Cloneable {
    /** The combatant this signal represents. */
    private CanHit canHit;
    /** The combatant's speed (cached at construction time). */
    private double speed;
    /** Time until the next action (lower = acts sooner). */
    private double time = 0;
    /** Accumulated action length since last turn. */
    private double length = 0;
    /** Unique identifier for this signal. */
    private int id = 0;

    /**
     * Creates a signal for the given combatant, caching its current speed.
     *
     * @param moveable the combatant to track
     */
    public Signal(CanHit moveable) {
        this.canHit = moveable;
        this.speed = moveable.getAttribute(AttributeType.SPEED).get();
    }

    @Override
    public int compareTo(@NotNull Signal o) {
        return Double.compare(this.time, o.getTime());
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
