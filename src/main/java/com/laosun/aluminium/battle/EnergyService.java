package com.laosun.aluminium.battle;

import com.laosun.aluminium.energy.EnergyGain;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.skill.Skill;

import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The one place energy is credited.
 *
 * <p><b>Why this is separate.</b> "Apply an energy gain" is a single arithmetic step with one side
 * effect (announce the credited amount when it is positive), and three callers - a direct grant, a
 * skill cast, and a kill/hit settlement - all have to agree on it. Keeping the crediting next to the
 * turn code made each caller look responsible for its own announcement.
 */
public final class EnergyService {

    private final BiConsumer<CanHit, Double> onCredited;

    /**
     * @param onCredited called with the target and the amount actually credited when it is positive
     */
    public EnergyService(BiConsumer<CanHit, Double> onCredited) {
        this.onCredited = onCredited;
    }

    /**
     * Credits one energy gain.
     *
     * @param target the one gaining energy
     * @param gain   the gain description
     * @return the value actually credited; {@code 0} when nothing can be credited
     */
    public double apply(CanHit target, EnergyGain gain) {
        if (target == null) {
            return 0;
        }
        double added = target.gainEnergy(gain);
        // "blocked by the cap" must not count as gaining energy.
        if (added > 0) {
            onCredited.accept(target, added);
        }
        return added;
    }

    /**
     * Grants energy by base value, through the energy gain rate.
     *
     * @param target the one gaining energy
     * @param amount the base amount
     * @return the value actually credited
     */
    public double grant(CanHit target, double amount) {
        return apply(target, EnergyGain.normal(amount));
    }

    /**
     * Credits the energy a skill cast grants.
     *
     * @param user       the caster
     * @param skill      the skill cast
     * @param hitTargets the actual hit set
     * @return the value actually credited
     */
    public double grantSkill(CanHit user, Skill skill, Set<? extends CanHit> hitTargets) {
        if (user == null) {
            return 0;
        }
        EnergyGain gain = user.getEnergyProvider().onSkillCast(user, skill, hitTargets);
        return gain == null ? 0 : apply(user, gain);
    }
}
