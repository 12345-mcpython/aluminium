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
}