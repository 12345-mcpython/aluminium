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
     * Whether this unit's ultimate is available right now.
     *
     * <p>This is the <b>gate</b> for casting, and it exists because "energy is full" is not the only
     * way to earn an ultimate. Characters who build stacks instead of energy (P8-8: Acheron's
     * 【残梦】, Feixiao's 【飞黄】, Cyrene's 【追忆】…) become ready when their **resource** is full, so
     * gating on {@code currentEnergy >= maxEnergy} would lock them out forever — their energy stays
     * at 0 by design.
     *
     * <p>Keeping the decision here rather than in {@code Battle} means the engine still does not know
     * which character it is looking at: the stack characters get a provider that reads their resource,
     * and the assembly point is the only place that knows the difference (P8-0).
     *
     * <p>The default is the conventional rule. Note it is <b>not</b> {@code isEnergyFull()} on
     * {@code CanHit}: the threshold can be lower than the cap ({@link
     * com.laosun.aluminium.models.SkillData#getSpNeed()}), which is why the caller passes the cost in.
     *
     * @param user      the unit asking
     * @param energyCost the energy threshold currently in force (the data's {@code spNeed} when it has
     *                   one, otherwise the unit's cap)
     * @return whether the ultimate may be cast
     */
    default boolean canCastUltra(CanHit user, double energyCost) {
        return user != null && user.hasEnergyBar() && user.getCurrentEnergy() >= energyCost;
    }

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
