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
     * <p>常规档：普攻 20 / 战技 30 / 终结技 5 —— 这正是引擎早先写死的
     * {@code Constant.ENERGY_GAIN_BASIC/SKILL/ULTRA}。落库之后有两类能被正确表达：
     * <ul>
     *   <li><b>完全不回能的角色</b>（{@code null}）：飞霄 1220、黄泉 1308、遐蝶 1407、
     *       白厄 1408、昔涟 1415、银狼LV.999 1506 —— 他们走的是"层数/特殊资源"，
     *       一个技能都不涨能量。</li>
     *   <li><b>离档值</b>：爻光 1502 普攻 = 30（不是 20）。</li>
     * </ul>
     *
     * <p>⚠ <b>多段/弹射技能的 {@code sp_base} 是"每段"值</b>（P3-0 实测）：艾丝妲/桑博/
     * 那刻夏/同谐开拓者 = 6、瓦尔特 = 10，乘段数后总量才是常规 30。
     * 每段是否真回能还受能力配置 {@code SPHitRatio} 控制，而**那个字段不在本项目数据里**，
     * 所以引擎当前**不做段数乘算**，直接取本值 —— 后果见 {@code EnergyGainDataTest} 的登记表。
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
