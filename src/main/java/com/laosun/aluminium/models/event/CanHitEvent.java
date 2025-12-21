package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

public interface CanHitEvent {
    default void onHealing(Battle battle, CanHit canHit, CanHit performer, double value) {
    }

    default void onReducingHealth(Battle battle, CanHit canHit, CanHit performer, double value) {
    }

    default void onHealthChange(Battle battle, CanHit canHit, CanHit performer, double value) {
    }

    default void onAttack(Battle battle, CanHit canHit, CanHit performer) {
    }

    default DeathMessage onHealthZero(Battle battle, CanHit canHit, CanHit performer) {
        return DeathMessage.ALLOW;
    }

    enum DeathMessage {
        ALLOW, DISALLOW
    }
}
