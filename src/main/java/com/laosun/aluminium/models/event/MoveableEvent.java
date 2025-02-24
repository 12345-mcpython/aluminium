package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Moveable;

public interface MoveableEvent {
    default void on_battle_start(Battle battle, Moveable moveable) {

    }
}
