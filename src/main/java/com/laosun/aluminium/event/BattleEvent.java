package com.laosun.aluminium.event;

import com.laosun.aluminium.Battle;

public interface BattleEvent {
    default void onBattleStart(Battle battle) {
    }
}
