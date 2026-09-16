package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Damage;

/**
 * Damage-settlement event, fired by {@code Battle.assemble} for both sides of a hit
 * before the zones are multiplied — the attacker first, then the defender.
 *
 * <p>{@link com.laosun.aluminium.models.CanHit CanHit} implements it and relays to its
 * {@code BuffManager}, so buffs (易伤 / 减伤 / 虚弱) inject their zones here; subclasses
 * may override it for character talents or boss mechanics.
 *
 * <p>Which side owns which zone (HSR.md §2.2): 增伤 = 攻击方增益（由属性装配）、
 * 虚弱 = 攻击方负面、易伤 = 受击方负面、减伤 = 受击方增益。
 */
public interface DamageEvent {
    default void onDamage(Battle battle, Damage damage) {
    }
}
