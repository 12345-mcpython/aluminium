package com.laosun.aluminium.models.energy;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.models.entity.CanHit;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.skill.Skill;

import java.util.Set;

/**
 * Conventional energy gain: basic attack 20 / skill 30 / ultimate 5 / taking a hit 10 /
 * kill 5 / break 5.
 *
 * <p>Where the numbers come from: the conventional tiers for basic
 * attack / skill / ultimate come from tbgd's {@code AvatarSkillConfig.SPBase} (ultimate is
 * always 5; multi-hit skills convert per hit and still total 30), while the base values for
 * taking a hit / kill / break were reverse-engineered from the character documents (the
 * documents only say "additionally restores N points"), so the numbers are centralised in
 * constants such as {@link Constant#ENERGY_GAIN_BASIC}.
 *
 * <p><b>Why we went back to constants</b>: we briefly switched to reading
 * {@code SkillData.spBase}, but in the data the {@code spBase} of multi-hit / bouncy skills
 * is a per-hit value (Asta 6, Welt 10) and would have to be multiplied by the hit count,
 * while that multiplication depends on the ability config's {@code SPHitRatio} (not present
 * in this project's data) - taking the raw value directly makes those 6 characters come out
 * low. Constants give the correct total, so going back to constants is more accurate. The
 * proper data-driven route is to aggregate {@code SPHitRatio} first and then wire it up.
 *
 * <p><b>Characters that do not use conventional energy are not judged here</b>: Feixiao /
 * Acheron / Castorice / Phainon / Cyrene / Silver Wolf LV.999 use stacks / special resources
 * and are injected at the assembly point by {@link NoConventionalEnergyProvider} - see
 * {@code CharacterFactory}.
 *
 * <p>Not handled for now: additional attacks (skills whose {@code AttackType} is empty or
 * is neither Normal nor BPSkill) gain no energy, techniques / maze skills gain no energy, and
 * character-level bonuses and special sources are left to their respective providers.
 */
public class StandardEnergyProvider implements EnergyProvider {

    @Override
    public EnergyGain onSkillCast(CanHit user, Skill skill, Set<? extends CanHit> hitTargets) {
        if (skill == null || skill.getData() == null) {
            return null;
        }
        // Note: Switch on the SkillCategory enum, never on a bare string: when the data side
        //    changes the spelling or adds a value, a string switch fails silently (it falls
        //    into default and is swallowed, with no compile-time protection).
        //    See {@code AGENT.md}.
        return switch (skill.getData().getCategory()) {
            case NORMAL -> EnergyGain.normal(energyOf(user, skill, Constant.ENERGY_GAIN_BASIC));
            case BPSKILL -> EnergyGain.normal(energyOf(user, skill, Constant.ENERGY_GAIN_SKILL));
            // Ultra goes through onUltCast; Maze / additional attacks (UNSPECIFIED) /
            // unknown values gain no energy at this stage
            default -> null;
        };
    }

    @Override
    public EnergyGain onUltCast(CanHit user, Skill skill) {
        // The ultimate's own row states 5 for every shipped character, so reading it changes nothing today - but it is
        // the same fact the data states, and the fallback is there for the rows that state nothing.
        return EnergyGain.normal(skill == null || skill.getData() == null || skill.getData().getSpBase() == null
                ? Constant.ENERGY_GAIN_ULTRA
                : skill.getData().getSpBase());
    }

    /**
     * How much energy one cast of this skill restores: the data's <b>own</b> {@code sp_base}, times the number of damage
     * instances when the row states a <b>per-hit</b> value.
     *
     * <h2>Why the data, and why the multiplication</h2>
     * {@code sp_base} is tbgd's {@code AvatarSkillConfig.SPBase}. For a single-target / AoE / blast skill it is the
     * <b>total</b> for the cast, while a <b>bounce</b> skill states it <b>per hit</b> (Asta 6 x 5 hits, Welt 10 x 3) -
     * which is why reading the raw number would have made those characters come out low. The multiplication used to be
     * deferred for want of the ability config's {@code SPHitRatio}; the actual instance count is the same number without
     * that table, and {@code EnergyDataConformanceTest} pins that the two agree for every shipped damaging skill.
     *
     * <p>Note: The constants remain the fallback for a row that states nothing (an enemy skill, a hand-built fixture),
     * so "no row" still behaves exactly as before.
     */
    private static double energyOf(CanHit user, Skill skill, double fallback) {
        Double stated = skill.getData().getSpBase();
        if (stated == null) {
            return fallback;
        }
        return stated * damageInstances(user, skill);
    }

    /**
     * How many damage instances one cast of this skill settles. Only {@code Bounce} needs it, because only its row
     * states a per-hit value; every other damaging shape's row states the total for the cast.
     */
    private static int damageInstances(CanHit user, Skill skill) {
        var data = skill.getData();
        if (data.getEffect() != com.laosun.aluminium.enums.SkillEffectType.BOUNCE
                || data.getSkills().isEmpty()) {
            return 1;
        }
        int level = Math.max(1, Math.min(user.skillLevel(skill), data.getSkills().size()));
        Integer extra = data.bounceAdditionalHits(data.bounceDamageShare(level), level);
        return 1 + (extra == null ? 0 : extra);
    }

    @Override
    public EnergyGain onTakingHit(CanHit target, Damage damage) {
        return damage == null ? null : EnergyGain.normal(Constant.ENERGY_GAIN_HIT);
    }

    @Override
    public EnergyGain onKill(CanHit attacker, CanHit target) {
        return attacker == null ? null : EnergyGain.normal(Constant.ENERGY_GAIN_KILL);
    }

    @Override
    public EnergyGain onBreak(CanHit attacker, CanHit target) {
        return attacker == null ? null : EnergyGain.normal(Constant.ENERGY_GAIN_BREAK);
    }
}
