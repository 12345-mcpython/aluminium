package com.laosun.aluminium.buff;

import com.laosun.aluminium.entity.CanHit;

/**
 * Contract of a buff / debuff: {@link BuffManager} is only responsible
 * for managing it (attach / remove / decrement each turn), changing attributes is the buff's
 * own job - {@link #applyEffect} attaches the modifier, {@link #removeBuff} takes it off.
 *
 * <p>Note: This division of labour is an already-settled design, not a todo: {@link AbstractBuff}
 * implements this interface, and attribute-type buffs (`SpeedBoostBuff` / `BoostDamageBuff` /
 * `TauntBuff` …) all change attributes inside `applyEffect` / `removeBuff`. This used to be three
 * lines of `// TODO` here, easily misread as "the architecture is not right yet", so it was turned
 * into an explanation.
 *
 * <p>Another easily confused point: injection-type buffs (`VulnerabilityBuff` /
 * `ReductionBuff`) have no persistent state; their `applyEffect` / `removeBuff` are empty and they
 * only inject the correction into the zones of that one segment's `Damage` at each settlement.
 * For how the two are told apart, see {@link StateBuff} and {@link DotBuff}.
 */
public interface Buff {

    /**
     * What this state <b>suppresses</b> while the holder carries it.
     *
     * <p><b>Why a closed set of words rather than a free field.</b> A suppression is not an attribute: 「无法使其他友方目标
     * 获得行动提前效果」 (1512's 【特邀嘉宾】) is a ban on an <b>action</b>, so it has to be read by the op that would perform
     * it. The vocabulary therefore belongs to the engine and is validated at load; {@code advance_to_others} is the only
     * word so far.
     *
     * @return the suppressed actions ({@code empty} for the ordinary state)
     */
    default java.util.Set<String> blocks() {
        return java.util.Set.of();
    }

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
