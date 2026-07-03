package com.laosun.aluminium.models;

import com.laosun.aluminium.battle.Battle;

import java.util.List;

/**
 * Implement on a {@link CanHit} to receive attack events.
 */
public interface AttackEvent {

    /**
     * Fires when this combatant attacks others.
     */
    default void onAttack(Battle b, CanHit attacker, List<CanHit> targets) {
    }

    /**
     * Fires when this combatant is attacked.
     */
    default void onBeAttacked(Battle b, CanHit self, CanHit attacker) {
    }
}
