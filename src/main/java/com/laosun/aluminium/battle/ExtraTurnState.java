package com.laosun.aluminium.battle;

import com.laosun.aluminium.entity.CanHit;

/**
 * The extra turn currently being taken, if any.
 *
 * <p><b>Why this is separate.</b> The start and end of an extra turn are two different places in the
 * turn loop ({@code stepForward} and {@code afterMove}); the state is what lets the end know that this
 * turn was extra. Keeping it beside the turn code made it look like the loop's own field.
 */
public final class ExtraTurnState {

    private CanHit actor;

    /**
     * @param actor the unit taking the extra turn
     */
    public void begin(CanHit actor) {
        this.actor = actor;
    }

    /**
     * @return the unit taking the extra turn, or {@code null}
     */
    public CanHit actor() {
        return actor;
    }

    /**
     * @param who the unit
     * @return whether {@code who} is the current extra-turn actor
     */
    public boolean isTakenBy(CanHit who) {
        return actor != null && actor == who;
    }

    /**
     * Ends the extra turn.
     */
    public void clear() {
        actor = null;
    }
}
