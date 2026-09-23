package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;

/**
 * Skill point (SP) **credited** event (P8-6): fired once after our side's SP really increases.
 *
 * <p>Why SP needs its own event instead of reusing {@link SkillCastEvent}:
 * Misha's "for **every 1 SP consumed** by our whole team → next ultimate +1 hit, Misha
 * restores 2 energy" and Sparkle's "when our side **consumes** SP, additionally restore 1
 * energy" both need to listen to the **fact** that "SP **were consumed**" — it is a change at
 * the **resource** level and is not the same thing as "which skill was cast":
 * <ul>
 *   <li>one cast may **neither gain nor lose** SP (ultimate, additional attack, talent);</li>
 *   <li>one cast may gain SP (basic attack +1);</li>
 *   <li>in the future there will also be SP changes **not caused by a skill cast** (technique
 *       opening +3, Passerby 4-piece opening +1).</li>
 * </ul>
 * That is exactly what "look up by skill type" hooks such as {@code EnergyProvider} /
 * {@code SkillPointPolicy} **cannot express**, so an event is required.
 *
 * <p>Emission point: forwarded by {@code Battle}'s {@code SkillPointListener} (notified by
 * the policy once the credit has really happened) — the policy is the only component that
 * knows "did it really increase this time, and by how much", so it reports and {@code Battle}
 * is responsible only for broadcasting.
 *
 * @param battle the running battle
 * @param amount the amount actually credited (the value after truncation by the cap, always
 *               {@code > 0})
 */
public interface SkillPointGainedEvent {
    default void onSkillPointGained(Battle battle, int amount) {
    }
}
