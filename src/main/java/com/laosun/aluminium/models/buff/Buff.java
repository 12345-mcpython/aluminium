package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.models.CanHit;

/**
 * Contract of a buff / debuff: {@link BuffManager} is only responsible
 * for **managing** it (attach / remove / decrement each turn), **changing attributes is the buff's
 * own job** — {@link #applyEffect} attaches the modifier, {@link #removeBuff} takes it off.
 *
 * <p>⚠ This division of labour is an **already-settled** design, not a todo: {@link AbstractBuff}
 * implements this interface, and attribute-type buffs (`SpeedBoostBuff` / `BoostDamageBuff` /
 * `TauntBuff` …) all change attributes inside `applyEffect` / `removeBuff`. This used to be three
 * lines of `// TODO` here, easily misread as "the architecture is not right yet", so it was turned
 * into an explanation.
 *
 * <p>Another easily confused point: **injection-type** buffs (`VulnerabilityBuff` /
 * `ReductionBuff`) have no persistent state; their `applyEffect` / `removeBuff` are empty and they
 * only inject the correction into the zones of **that one segment's** `Damage` at each settlement.
 * For how the two are told apart, see {@code engine.md} §10.2.
 */
public interface Buff {
    CanHit getSource();

    void setSource(CanHit source);

    // true can continue move
    // false can ignore the move
    boolean canAct();

    // apply attribute boost at the buff append
    void applyEffect(CanHit target);

    // remove attribute boost at the buff over
    void removeBuff(CanHit target);

    // tick it
    void tickEffect(CanHit target);

    int duration();
}
