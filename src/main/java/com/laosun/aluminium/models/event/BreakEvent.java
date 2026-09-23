package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.CanHit;

/**
 * Weakness break event (P8-6): fired once when an enemy **has just been broken** (the moment the
 * toughness bar hits zero).
 *
 * <p>The trigger point is unique: the one place in {@code Battle.reduceToughness} that drains the
 * toughness and sets {@code broken} — so "an enemy already in the broken state keeps being hit"
 * (the super break computation path) **does not** fire it again.
 * Himeko's "break weakness +1【充能】", as well as P4-4's action bar push / DoT attachment, all
 * happen around this same moment.
 *
 * <p>⚠ Difference from {@link KillEvent}: a break does not necessarily kill, and a death does not
 * necessarily go through a break.
 *
 * @param battle    the running battle
 * @param attacker  the one causing the break
 * @param target    the enemy being broken
 * @param element   break element (decides which DOT is attached: Fire = burn / Lightning = shock / Physical = bleed / Wind = wind shear)
 */
public interface BreakEvent {
    default void onBreak(Battle battle, CanHit attacker, CanHit target, DamageElement element) {
    }
}
