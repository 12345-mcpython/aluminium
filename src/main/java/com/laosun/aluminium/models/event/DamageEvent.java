package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Damage;

/**
 * Damage-settlement event, fired by {@code Battle.assemble} for both sides of a hit
 * before the zones are multiplied - the attacker first, then the defender.
 *
 * <p>{@link com.laosun.aluminium.models.CanHit CanHit} implements it and relays to its
 * {@code BuffManager}, so buffs (vulnerability / reduction / weakness) inject
 * their zones here; subclasses may override it for character talents or boss mechanics.
 *
 * <p>Which side owns which zone (HSR.md §2.2): DMG boost = attacker-side buff (assembled from
 * attributes), weakness = attacker-side debuff, vulnerability = defender-side debuff,
 * reduction = defender-side buff.
 */
public interface DamageEvent {
    default void onDamage(Battle battle, Damage damage) {
    }
}
