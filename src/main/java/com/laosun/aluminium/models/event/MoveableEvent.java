package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Moveable;

/**
 * Event system for {@link Moveable}
 * @author laosun
 * @since core version 1.0.0
 */

public interface MoveableEvent {
    /**
     * When the battle start call this
     */
    default void onBattleStart(Battle battle, Moveable moveable) {

    }
}
