package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.Skill;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillEffectType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runtime view of a skill loaded from {@code skills.json}, resolved for a
 * specific character + skill-id combination.
 *
 * <p>Provides the per-level parameter list {@link #getSkills()}, the attack
 * element {@link #getElement()} and the effect classification
 * {@link #getEffect()}. Access is immutable read-only; resolve lazily via
 * {@link #init(int, int)} which caches nothing but reads from
 * {@link Constant#SKILLS}.
 *
 * <p><b>Non-damaging skills</b> (heal / support / summon / ...) may have
 * {@code element == null} (the raw data uses {@code "Unknown"}); their
 * {@code effect} tells the executor which pipeline to run.
 *
 * @see SkillEffectType
 * @see DamageElement
 */
@Getter
@AllArgsConstructor
public class SkillData {
    private static final List<List<Double>> EMPTY_PARAMS = Collections.emptyList();
    private static final Skill.StanceList EMPTY_STANCE = new Skill.StanceList(0, 0, 0);

    /**
     * Fallback skill data returned when the requested character / skill id is not found
     * in the loaded data set.
     */
    private static final SkillData EMPTY = new SkillData(0, "", EMPTY_PARAMS, EMPTY_STANCE,
            DamageElement.PHYSICAL, SkillEffectType.ENHANCE);

    /**
     * Maximum possible skill level (length of {@link #skills}).
     */
    private final int maxLevel;
    /**
     * Attack category string from the data, e.g. {@code "Normal"}, {@code "BPSkill"},
     * {@code "Ultra"}, {@code "Maze"}.
     */
    private final String skillType;
    /**
     * Per-level parameter lists, indexed by {@code level - 1}.
     */
    private final List<List<Double>> skills;
    /**
     * Toughness-reduction per-target counts: single / all / spread.
     */
    private final Skill.StanceList stanceList;
    /**
     * Damage element of this skill, or {@code null} for non-damaging skills.
     */
    private final DamageElement element;
    /**
     * Effect classification of this skill (attack / heal / buff / ...).
     */
    private final SkillEffectType effect;

    /**
     * Resolves the skill data of a character skill from the global data set.
     *
     * @param cid     character id (key of {@code skills.json})
     * @param skillID skill slot id (e.g. 1 = normal attack, 2 = basic skill,
     *                3 = ultimate, 4 = talent, 6 = maze attack, 7 = technique)
     * @return the skill data for the requested skill
     * @throws IllegalArgumentException if the character / skill id exists but its
     *                                  {@code skill_effect} is missing or unknown
     */
    public static SkillData init(int cid, int skillID) {
        Map<Integer, Skill> skillMap = Constant.SKILLS.get(cid);
        if (skillMap == null) {
            return EMPTY;
        }
        Skill skill = skillMap.get(skillID);
        if (skill == null) {
            return EMPTY;
        }
        if (skill.skillEffect() == null) {
            throw new IllegalArgumentException("Missing skill_effect: cid=" + cid + ", skillID=" + skillID);
        }
        SkillEffectType effect = SkillEffectType.fromString(skill.skillEffect());
        if (effect == null) {
            throw new IllegalArgumentException(
                    "Unknown skill_effect: " + skill.skillEffect() + " (cid=" + cid + ", skillID=" + skillID + ")");
        }
        return new SkillData(skill.maxLevel(), skill.attackType(), skill.paramList(), skill.stanceList(),
                skill.element(), effect);
    }
}
