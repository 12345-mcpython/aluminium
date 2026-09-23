package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.DamageElement;
import lombok.Getter;

/**
 * Damage over time attached to a weakness break (P4-5): burn (Fire) / shock (Lightning) /
 * bleed (Physical) / wind shear (Wind).
 *
 * <p>The simplest form, independent of the Buff system — it only tracks "how much is deducted each
 * turn, how many turns are left"; at the start of each turn {@code Battle.tickDots(Enemy)} settles
 * it in **application order** (HSR.md §7 "first applied, first settled").
 *
 * <p>DOT damage goes through the full zone set (it takes DMG boost, DEF / RES / vulnerability),
 * but it **cannot crit** — expressed by
 * {@link com.laosun.aluminium.enums.DamageType#DOT}'s own
 * {@code (crittable=false, boostable=true)}, so there is no need to test for it here.
 */
@Getter
public class Dot {

    /**
     * The applier ({@code Damage}'s attacker is not allowed to be null, so the source MUST be recorded).
     */
    private final CanHit source;
    /**
     * DoT element: Fire / Lightning / Physical / Wind (Ice = freeze, Quantum = entanglement,
     * Imaginary = imprisonment, unified into a table in P10-1).
     */
    private final DamageElement element;
    /**
     * Base damage per settlement (break base × {@code Constant.DOT_RATIO}).
     */
    private final double baseDamage;
    /**
     * Remaining number of settlements.
     */
    private int remainingTurns;

    /**
     * @param source         the applier
     * @param element        the DoT element
     * @param baseDamage     base damage per settlement (has not been through the zones yet)
     * @param remainingTurns how many more times it can settle
     */
    public Dot(CanHit source, DamageElement element, double baseDamage, int remainingTurns) {
        this.source = source;
        this.element = element;
        this.baseDamage = baseDamage;
        this.remainingTurns = remainingTurns;
    }

    /**
     * Settles once (called by {@code Battle.tickDots} after the HP deduction is done).
     *
     * @return {@code true} = this was the last one, the caller should remove it from the target
     */
    public boolean tick() {
        remainingTurns--;
        return remainingTurns <= 0;
    }
}
