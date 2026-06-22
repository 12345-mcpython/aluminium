package com.laosun.aluminium.enums;

/**
 * The type of effect a skill produces.
 */
public enum SkillEffectType {
    /** Deals damage to the target. */
    DAMAGE,
    /** Restores HP to the target. */
    HEAL,
    /** Applies a positive status effect. */
    BUFF,
    /** Applies a negative status effect. */
    DEBUFF,
    /** Summons an additional entity onto the field. */
    SUMMON
}
