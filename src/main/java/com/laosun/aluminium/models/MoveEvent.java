package com.laosun.aluminium.models;

import com.laosun.aluminium.battle.Battle;

/**
 * Implement on a {@link CanHit} to receive movement / turn events.
 */
public interface MoveEvent {

    /**
     * Fires once when the battle starts.
     */
    default void onBattleStart(Battle b, CanHit self) {
    }

    /**
     * Fires when this combatant is about to act (after pushQueue, before action).
     */
    default void beforeMove(Battle b, CanHit self) {
    }

    /**
     * Fires after this combatant completes an action (after setTopZero).
     */
    default void afterMove(Battle b, CanHit self) {
    }
}
