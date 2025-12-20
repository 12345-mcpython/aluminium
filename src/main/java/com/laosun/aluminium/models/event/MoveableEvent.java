package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

public interface MoveableEvent {
    default void onBattleStart(Battle battle, CanHit canHit) {
    }

    default void onBeforeMove(Battle battle) {
    }

    default void onAfterMove(Battle battle, CanHit canHit) {
    }

    default void onBeAttacked(Battle battle, CanHit attacker) {
    }

    default void onTakeDamage(double damage) {
    }

    default void onDeath() {
    }
}