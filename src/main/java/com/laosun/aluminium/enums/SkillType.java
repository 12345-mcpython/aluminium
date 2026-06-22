package com.laosun.aluminium.enums;

/**
 * The category of a character's skill.
 *
 * <p>Corresponds to the different types of abilities in Honkai: Star Rail.
 */
public enum SkillType {
    /** Basic attack. */
    COMMON,
    /** Skill (consumes skill points). */
    SKILL,
    /** Ultimate (energy-based). */
    ULTRA,
    /** Passive talent. */
    TALENT,
    /** Active skill of a summoned entity. */
    SUMMON_SKILL,
    /** Passive talent of a summoned entity. */
    SUMMON_TALENT
}
