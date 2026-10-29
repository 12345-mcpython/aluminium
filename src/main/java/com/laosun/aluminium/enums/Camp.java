package com.laosun.aluminium.enums;
import com.laosun.aluminium.entity.CanHit;

/**
 * Represents the faction or alignment of a combatant in the action queue.
 *
 * <p>Used by {@link com.laosun.aluminium.entity.CanHit} and its subclasses
 * to determine team affiliation (e.g. PLAYER entities are friendly to each other).
 */
public enum Camp {
    /**
     * Player-controlled characters and summons.
     */
    PLAYER,
    /**
     * Enemy combatants.
     */
    ENEMY,
    /**
     * Neutral entities that are not aligned with either side.
     */
    NEUTRAL
}
