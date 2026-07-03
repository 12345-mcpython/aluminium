package com.laosun.aluminium.models;

import com.laosun.aluminium.battle.Battle;

/**
 * Implement on a {@link CanHit} to handle kill events.
 */
public interface KillEvent {

    /**
     * Fires when a player character is killed.
     *
     * @return {@code false} to revive the character (HP restored to 1).
     */
    default boolean onCharacterKilled(Battle b, CanHit killer, CanHit character) {
        return true;
    }

    /**
     * Fires when an enemy is killed.
     *
     * @return {@code false} to revive the enemy (HP restored to 1).
     */
    default boolean onEnemyKilled(Battle b, CanHit killer, CanHit enemy) {
        return true;
    }
}
