package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;

/**
 * Skill point (战技点, SP) **spent** event (P8-6): fired once after our skill points really decrease.
 *
 * <p>⚠ <b>It only fires when it was "really spent"</b>: when there are not enough skill points the whole action
 * does not happen ({@code performAction} returns {@code false}, nothing is queued, no damage), and this event
 * **must not** be fired — otherwise counters like Misha's/Sparkle's "every 1 point spent" would record a spend
 * that never happened. This is pinned down by {@code EventBusTest}.
 *
 * <p>See also the note on {@link SkillPointGainedEvent} (why skill points need their own events).
 *
 * @param battle the running battle
 * @param amount the amount actually spent (always {@code > 0}; under the current standard rules always 1)
 */
public interface SkillPointSpentEvent {
    default void onSkillPointSpent(Battle battle, int amount) {
    }
}
