package com.laosun.aluminium.battle;

import com.laosun.aluminium.enums.TriggerEvent;

/**
 * The state of one nested trigger firing: its depth and the damage settled by the outermost firing.
 *
 * <p><b>Why this is separate.</b> The depth is a recursion guard, and the settled total must be reset
 * only at the outermost firing - a nested event is part of the same action. Both are firing state, not
 * battle state; keeping them apart makes the reset rule one place.
 */
public final class TriggerFiring {

    /**
     * How deep nested trigger firing may go before the engine gives up.
     */
    public static final int MAX_DEPTH = 8;

    private int depth;
    private double settledDamage;

    /**
     * Opens one firing.
     *
     * @param event the event being fired
     * @throws IllegalStateException when the nesting cap is reached
     */
    public void begin(TriggerEvent event) {
        if (depth >= MAX_DEPTH) {
            throw new IllegalStateException(
                    "Trigger recursion exceeded " + MAX_DEPTH + " levels while firing "
                            + event.value() + "; a trigger table is probably reacting to its own effect");
        }
        // Only the outermost firing starts a new total: a nested event is part of the same action.
        if (depth == 0) {
            settledDamage = 0;
        }
        depth++;
    }

    /**
     * Closes one firing.
     */
    public void end() {
        depth--;
    }

    /**
     * @param amount the damage settled by this instance
     */
    public void addSettledDamage(double amount) {
        settledDamage += amount;
    }

    /**
     * @return the damage settled by the outermost firing
     */
    public double settledDamage() {
        return settledDamage;
    }
}
