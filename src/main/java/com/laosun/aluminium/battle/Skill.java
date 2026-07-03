package com.laosun.aluminium.battle;

import java.util.List;

/**
 * A skill definition with one or more effects.
 */
public record Skill(String name, SkillType type, List<SkillEffect> effects) {

    public enum SkillType {COMMON, SKILL, ULTRA}

    public enum EffectType {DAMAGE, HEAL, ADVANCE}

    public enum StatScale {ATK, HP, DEF}

    public enum TargetScope {SINGLE_ENEMY, THREE_ENEMIES, ALL_ENEMIES, SINGLE_ALLY, THREE_ALLIES, ALL_ALLIES, SELF}

    /**
     * A single effect within a skill.
     */
    public record SkillEffect(EffectType effect, StatScale scale, double multiplier, TargetScope scope,
                              int targetCount) {
    }
}
