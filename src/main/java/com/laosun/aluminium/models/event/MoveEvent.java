package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;

public interface MoveEvent {
    default void beforeMove(Battle battle) {
    }

    default void afterMove(Battle battle) {
    }
}
