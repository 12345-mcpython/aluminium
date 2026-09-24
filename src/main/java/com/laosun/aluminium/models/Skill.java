package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;

import java.util.List;

public abstract class Skill {
    public abstract int getLevel();

    public abstract SkillData getData();

    public abstract void execute(Battle battle, CanHit user, List<? extends CanHit> target);

    /**
     * The character this skill belongs to, or {@code 0} when the skill has no real identity.
     *
     * <p>Together with {@link #getSkillSlot()} this is the key into the data tables that are indexed
     * by character rather than embedded in {@code skills.json} — today {@code skill_effects.json}
     * (P10-3: how to read a non-damaging skill's parameters).
     *
     * <p>The default is deliberately {@code 0} rather than abstract: hand-built test skills and the
     * {@code Character.fromAttributes} placeholder simply have no character, and forcing every one of
     * them to answer would be noise. Callers must treat {@code 0} as "no table entry", never as a
     * character id — the same convention as {@code Character.getCid()}.
     */
    public int getCid() {
        return 0;
    }

    /**
     * The skill's slot ({@code 1} = normal attack, {@code 2} = skill, {@code 3} = ultimate,
     * {@code 4} = talent, {@code 6} = overworld attack, {@code 7} = technique), or {@code 0} when
     * unknown.
     *
     * <p>It is the key {@code skills.json} itself is indexed by, so no arithmetic on ids is needed.
     *
     * @see #getCid()
     */
    public int getSkillSlot() {
        return 0;
    }
}
