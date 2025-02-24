package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Moveable;

public interface MoveableEvent {
    default void onBattleStart(Battle battle, Moveable moveable) {

    }
}
