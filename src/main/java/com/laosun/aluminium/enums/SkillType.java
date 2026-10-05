package com.laosun.aluminium.enums;

/**
 * The category of a character's skill.
 *
 * <p>Corresponds to the different types of abilities in Honkai: Star Rail.
 *
 * <p>The split is by "when it enters the battle model", and this distinction matters a lot (see
 * {@link #isIntrinsic()}):
 * <ul>
 *   <li><b>Permanent</b>: {@link #COMMON} / {@link #SKILL} / {@link #ULTRA} / {@link #TALENT}
 *        - a character should already have these the moment it is created; assembled by
 *       {@code Character.Builder.build()};</li>
 *   <li><b>Attached at battle start</b>: {@link #MAZE} / {@link #TECHNIQUE}
 *        - things used on the map; attached by {@code Battle.startBattle()}, not permanently carried
 *       on the character.</li>
 * </ul>
 */
public enum SkillType {
    /**
     * Basic attack.
     */
    COMMON,
    /**
     * Skill (consumes skill points).
     */
    SKILL,
    /**
     * Ultimate (energy-based).
     */
    ULTRA,
    /**
     * Passive talent.
     */
    TALENT,
    /**
     * Map basic attack (skill slot 6): the one that attacks monsters on the overworld map, and also
     * the hit that "reduces the matching-element toughness when entering battle".
     *
     * <p>The attack type in the data is {@code MazeNormal}, the effect is {@code MazeAttack}.
     * It is not the in-battle basic attack (that is {@link #COMMON}).
     */
    MAZE,
    /**
     * Technique (秘技) (skill slot ): a buff actively cast on the map (attack type {@code Maze} in
     * the data).
     *
     * <p>For most characters the technique's effect is "takes effect when the next battle starts"
     * (for example Jing Yuan (景元) +3 stacks of [神君] (Lightning-Lord)), so its trigger timing
     * is the start of battle - see the event completion work in ROADMAP P8-6.
     */
    TECHNIQUE,
    /**
     * Active skill of a summoned entity.
     */
    SUMMON_SKILL,
    /**
     * Passive talent of a summoned entity.
     */
    /**
     * The data's slot 20 (2026-09-30): the Elation skill "欢愉技", named by the game's own text
     * (relic set 130 says "首次使用欢愉技"). Nine skills in `skills.json` carry `attack_type: ElationDamage`
     * and every one of them was invisible to the loader until this constant existed.
     */
    ELATION_SKILL,
    /**
     * Note: The data's slot 21 (2026-09-30): a second Elation-damage skill carried by 1506 and 1513 only. The game text
     * does not name it, so this name records the SLOT and nothing more.
     */
    ELATION_EXTRA,
    SUMMON_TALENT;

    /**
     * Is this slot a character-permanent skill (one a character should have the moment it is created)?
     *
     * <p>{@link #MAZE} / {@link #TECHNIQUE} return {@code false}: they are only attached at the start of
     * battle, so a character built by {@code CharacterFactory} does not carry them
     * (for the assembly point see {@code Constant.SKILL_SLOT} and {@code Battle#startBattle()}).
     *
     * <p>The two summon slots also return {@code false}: they belong to a memosprite/summon, not to the
     * character's own skills (P9-4).
     */
    public boolean isIntrinsic() {
        // 2026-09-30: the Elation damage skills are part of the always-on kit. This method is the gate that kept
        // them invisible: `Character`'s kit builder skips every type it does not call intrinsic.
        return this == COMMON || this == SKILL || this == ULTRA || this == TALENT
                || this == ELATION_SKILL || this == ELATION_EXTRA;
    }
}
