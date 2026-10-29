package com.laosun.aluminium.event;

import com.laosun.aluminium.Battle;

public interface MoveEvent {
    default void beforeMove(Battle battle) {
    }

    default void afterMove(Battle battle) {
    }
}
