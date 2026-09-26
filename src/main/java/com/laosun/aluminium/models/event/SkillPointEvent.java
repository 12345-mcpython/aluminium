package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;

public interface SkillPointEvent {
    default void onSkillPointGained(Battle battle, int amount) {
    }

    default void onSkillPointSpent(Battle battle, int amount) {
    }
}
