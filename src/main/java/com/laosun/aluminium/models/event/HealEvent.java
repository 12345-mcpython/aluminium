package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

/**
 * Heal event (P8-6): fired once after someone's HP is **actually restored**.
 *
 * <p>{@code actuallyHealed} is the **amount actually restored**, not the base healing amount passed in —
 * being healed at full HP is 0, and then it **does not fire** ("received healing" and "got HP back" are two
 * different things, and healing-type trigger sources care about the latter).
 *
 * <p>Emission point: {@code Battle.heal(healer, target, base)}.
 * ⚠ {@code CanHit.heal(double)} (the raw HP addition) **does not fire** — it is the low-level opening shared by
 * {@code Battle.heal} and "directly set the HP", and emitting on it would treat "directly setting HP" as healing
 * too.
 *
 * @param battle         the running battle
 * @param healer         the healer (may be {@code null}, e.g. field/blessing recovery)
 * @param target         the one who was healed
 * @param actuallyHealed the amount actually restored (always {@code > 0})
 */
public interface HealEvent {
    default void onHeal(Battle battle, CanHit healer, CanHit target, double actuallyHealed) {
    }
}
