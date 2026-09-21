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
            DamageElement.PHYSICAL, SkillEffectType.ENHANCE, null, null);

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
     * 开大阈值（{@code skills.json} 的 {@code sp_need}）：**只有终结技有值**，其余为 {@code null}。
     *
     * <p>它就是 tbgd {@code AvatarSkillConfig.SPNeed}。⚠ **不等于能量上限**：93 个角色里有 5 个
     * 两者的比值是 2:1（云璃 240/120、银枝 180/90、绯英 480/240、飞霄 12/6、昔涟 24/12）。
     * 另见 {@link #spBase}。
     */
    private final Double spNeed;
    /**
     * 施放这个技能**回多少能量**（{@code skills.json} 的 {@code sp_base}）：tbgd
     * {@code AvatarSkillConfig.SPBase}。
     *
     * <p>常规档：普攻 20 / 战技 30 / 终结技 5 —— 与 {@code Constant.ENERGY_GAIN_*} 一致。
     *
     * <p>⚠ <b>本字段目前不驱动回能</b>（{@link com.laosun.aluminium.models.energy.StandardEnergyProvider}
     * 仍用常量）。原因是数据里**多段/弹射技能的 {@code sp_base} 是"每段"值**
     * （艾丝妲/桑博/那刻夏/同谐开拓者 6、瓦尔特 10），乘段数才对，而段数乘算依赖能力配置的
     * {@code SPHitRatio}（本项目数据里没有）。常量给出的反而是**正确总量**。
     * 数据化的正路见 ROADMAP P3-4（先聚合 {@code SPHitRatio}）。
     *
     * <p>保留读取的价值：它是 {@code SPHitRatio} 聚合的输入，也是"哪些技能不回能"的原始事实
     * （{@code null} = 该技能不回能）。
     */
    private final Double spBase;

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
                skill.element(), effect, skill.spNeed(), skill.spBase());
    }
}
