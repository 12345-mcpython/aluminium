package com.laosun.aluminium.models.energy;

import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.skill.Skill;

import java.util.Set;

/**
 * The {@link EnergyProvider} for characters that **do not use conventional energy**: all 5
 * hooks return {@code null} (= not credited).
 *
 * <p>It applies to "stack / special resource" characters — what they build up in the game is
 * not energy but resources such as 【追忆】/【新蕊】/【火种】/points (Feixiao 1220, Acheron 1308,
 * Castorice 1407, Phainon 1408, Cyrene 1415, Silver Wolf LV.999 1506). The assembly point is
 * {@link com.laosun.aluminium.utils.CharacterFactory#create(int, int)}.
 *
 * <p><b>Why it must be a separate provider rather than a null check inside
 * {@link StandardEnergyProvider}</b>:
 * <ul>
 *   <li>This is a **design classification** ("this character does not use the conventional
 *       energy system"), not a single data fact. The P8-0 three-way split assigns judgements
 *       like this to the provider / assembly point, which is also the only place allowed to
 *       mention {@code cid}.</li>
 *   <li>The conventional provider has 5 hooks. Blocking only the two skill ones
 *       ({@code sp_base == null}) would leave {@code onTakingHit} / {@code onKill} /
 *       {@code onBreak} still granting energy — with severe measured consequences:
 *       {@code castUltra}'s threshold is {@code currentEnergy >= maxEnergy}, while Acheron's
 *       cap is only **9** and Feixiao's / Phainon's only **12**, so "taking a hit or two"
 *       fills them up and **unleashes an ultimate that should not exist** (their slot 3
 *       really is an {@code Ultra} skill).</li>
 * </ul>
 *
 * <p>Once this is wired up, these characters behave as "energy is always 0, an ultimate can
 * never be cast" — an **explicit and testable** state, rather than relying on a data
 * coincidence to block one path and miss three. When the P8-8 {@code Resource} abstraction
 * lands, simply swap this provider for a real resource implementation (the wiring point is
 * already in place).
 *
 * <p>⚠ Note the distinction from {@code maxEnergy == 0} (**no energy bar at all**, e.g.
 * Castorice 1407): in that case {@code CanHit.gainEnergy} is already a no-op, so using this
 * provider or not makes no difference. This provider is about characters that **do** have an
 * energy pool but must not gain it through conventional routes (Acheron 9, Feixiao 12…).
 */
public class NoConventionalEnergyProvider implements EnergyProvider {

    @Override
    public EnergyGain onSkillCast(CanHit user, Skill skill, Set<? extends CanHit> hitTargets) {
        return null;
    }

    @Override
    public EnergyGain onUltCast(CanHit user, Skill skill) {
        return null;
    }

    @Override
    public EnergyGain onTakingHit(CanHit target, Damage damage) {
        return null;
    }

    @Override
    public EnergyGain onKill(CanHit attacker, CanHit target) {
        return null;
    }

    @Override
    public EnergyGain onBreak(CanHit attacker, CanHit target) {
        return null;
    }
}
