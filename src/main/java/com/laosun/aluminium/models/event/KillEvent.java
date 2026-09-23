package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

/**
 * Kill event (P8-6): fired once after a unit **is killed**.
 *
 * <p>The test is "this instance of damage took the target from alive to dead" (the return value of
 * {@code takeDamage} inside {@code Battle.applyDamage}), so:
 * <ul>
 *   <li><b>fires</b>: basic attack / skill / ultimate / break damage / super break / DOT / additional damage /
 *       true damage — any damage attributed to someone that kills the target fires it (it does **not** look at
 *       {@code countsAsAttack}, matching the energy-on-kill rule: an additional damage last hit also counts as a
 *       kill);</li>
 *   <li><b>does not fire</b>: the target **is already dead** and gets hit again ({@code applyDamage} returns 0
 *       immediately), during phase-transition invulnerability ({@code isInvulnerable}, which also loses no HP, so
 *       it is naturally not a kill), and non-damage deaths such as "HP was already ≤0 and the unit is removed from
 *       the action bar".</li>
 * </ul>
 *
 * <p>Emission point: {@code Battle.applyDamage} (the internal settlement overload), next to {@link HpLossEvent}.
 * Hanging it in {@code grantKillEnergy} would be equivalent, but it would be missed there because of the
 * "energy on kill is only counted once" gate ({@code EnergyGrant}) — an event describes a **fact**, and must not be
 * filtered by an energy-gain rule.
 *
 * @param battle   the running battle
 * @param attacker the killer ({@code damage.getAttacker()}, may be {@code null})
 * @param victim   the one who was killed
 */
public interface KillEvent {
    default void onKill(Battle battle, CanHit attacker, CanHit victim) {
    }
}
