package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;

public interface BattleEvent {
    default void onBattleStart(Battle battle) {
    }
}
