package com.laosun.aluminium.models.energy;

import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Skill;

import java.util.Set;

/**
 * Energy gain rules (P3).
 *
 * <p>Regular characters use {@link StandardEnergyProvider} (basic attack 20 / skill 30 / ultimate 5 / taking a hit
 * 10 / kill 5 / break 5); special characters implement this interface on their own later, and the mechanics side
 * does not need a single line of code changed for a character.
 *
 * <p>Conventions:
 * <ul>
 *   <li>A method returning {@code null} = that event gives no energy; an implementation only overrides the hooks it
 *   cares about.</li>
 *   <li>The returned {@link EnergyGain} is only "base value + whether it benefits from energy gain rate"; the
 *   actual crediting is done by {@link CanHit#gainEnergy(EnergyGain)} (clamping to the cap, returning the amount
 *   actually credited).</li>
 *   <li>No HP loss / cap / death checks happen here — that is the caller's ({@code Battle}) business.</li>
 * </ul>
 */
public interface EnergyProvider {

    /**
     * When a skill is cast (basic attack / skill / any other non-ultimate slot).
     *
     * @param user       the caster
     * @param skill      the skill cast
     * @param hitTargets the targets actually hit this time (may be an empty set: buff/healing skills hit nobody)
     * @return the energy gain description, {@code null} = no energy gain
     */
    default EnergyGain onSkillCast(CanHit user, Skill skill, Set<? extends CanHit> hitTargets) {
        return null;
    }

    /**
     * When the ultimate is cast (the caller has already zeroed the energy first, so what is returned here is
     * "how much comes back after the ultimate is cast").
     *
     * @param user  the caster
     * @param skill the ultimate
     * @return the energy gain description, {@code null} = no energy gain
     */
    default EnergyGain onUltCast(CanHit user, Skill skill) {
        return null;
    }

    /**
     * After taking one instance of damage.
     *
     * @param target the one who was hit
     * @param damage the instance of damage that has already been credited (the caller only calls this when
     *               {@code isCountsAsAttack()})
     * @return the energy gain description, {@code null} = no energy gain
     */
    default EnergyGain onTakingHit(CanHit target, Damage damage) {
        return null;
    }

    /**
     * After killing a target.
     *
     * @param attacker the killer ({@code damage.getAttacker()})
     * @param target   the target that was killed
     * @return the energy gain description, {@code null} = no energy gain
     */
    default EnergyGain onKill(CanHit attacker, CanHit target) {
        return null;
    }

    /**
     * After breaking a weakness.
     *
     * @param attacker the one who caused the break
     * @param target   the target that was broken
     * @return the energy gain description, {@code null} = no energy gain
     */
    default EnergyGain onBreak(CanHit attacker, CanHit target) {
        return null;
    }
}
