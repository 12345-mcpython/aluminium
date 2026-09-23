package com.laosun.aluminium.models.energy;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Skill;

import java.util.Set;

/**
 * Conventional energy gain: basic attack 20 / skill 30 / ultimate 5 / taking a hit 10 /
 * kill 5 / break 5.
 *
 * <p>Data sources are in {@code ROADMAP.md} under P3-0: the conventional tiers for basic
 * attack / skill / ultimate come from tbgd's {@code AvatarSkillConfig.SPBase} (ultimate is
 * always 5; multi-hit skills convert per hit and still total 30), while the base values for
 * taking a hit / kill / break were reverse-engineered from the character documents (the
 * documents only say "additionally restores N points"), so the numbers are centralised in
 * constants such as {@link Constant#ENERGY_GAIN_BASIC}.
 *
 * <p><b>Why we went back to constants</b> (2026-09-21): we briefly switched to reading
 * {@code SkillData.spBase}, but in the data the {@code spBase} of multi-hit / bouncy skills
 * is a **per-hit value** (Asta 6, Welt 10) and would have to be multiplied by the hit count,
 * while that multiplication depends on the ability config's {@code SPHitRatio} (not present
 * in this project's data) — taking the raw value directly makes those 6 characters come out
 * low. Constants give the **correct total**, so going back to constants is more accurate. The
 * proper data-driven route is ROADMAP P3-4 (aggregate {@code SPHitRatio} first, then wire it up).
 *
 * <p><b>Characters that do not use conventional energy are not judged here</b>: Feixiao /
 * Acheron / Castorice / Phainon / Cyrene / Silver Wolf LV.999 use stacks / special resources
 * and are injected at the assembly point by {@link NoConventionalEnergyProvider} — see
 * {@code CharacterFactory} and {@code engine.md} §9.5.
 *
 * <p>**Not handled** for now: additional attacks (skills whose {@code AttackType} is empty or
 * is neither Normal nor BPSkill) gain no energy, techniques / maze skills gain no energy, and
 * character-level bonuses and special sources are left to their respective providers (P8-3).
 */
public class StandardEnergyProvider implements EnergyProvider {

    @Override
    public EnergyGain onSkillCast(CanHit user, Skill skill, Set<? extends CanHit> hitTargets) {
        if (skill == null || skill.getData() == null) {
            return null;
        }
        // ⚠ Switch on the SkillCategory enum, never on a bare string: when the data side
        //    changes the spelling or adds a value, a string switch fails silently (it falls
        //    into default and is swallowed, with no compile-time protection).
        //    See F-6 in DOC_VS_CODE.md §F.
        return switch (skill.getData().getCategory()) {
            case NORMAL -> EnergyGain.normal(Constant.ENERGY_GAIN_BASIC);
            case BPSKILL -> EnergyGain.normal(Constant.ENERGY_GAIN_SKILL);
            // Ultra goes through onUltCast; Maze / additional attacks (UNSPECIFIED) /
            // unknown values gain no energy at this stage
            default -> null;
        };
    }

    @Override
    public EnergyGain onUltCast(CanHit user, Skill skill) {
        return EnergyGain.normal(Constant.ENERGY_GAIN_ULTRA);
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
