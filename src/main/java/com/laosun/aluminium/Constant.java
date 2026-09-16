package com.laosun.aluminium;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.*;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.utils.JSONReader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global constants and static game data loaded at startup.
 *
 * <p>All game data files under {@code resources/data/} are deserialized via
 * {@link JSONReader} when this class is first loaded. The following data sets are available:
 * <ul>
 *   <li>{@link #RELIC_MAIN_ATTRIBUTES} — main attribute value tables by star level</li>
 *   <li>{@link #RELIC_SUB_ATTRIBUTES} — sub-attribute value tables by star level</li>
 *   <li>{@link #WEAPONS} — weapon (light cone) data by ID</li>
 *   <li>{@link #CHARACTERS} — character base stats by ID</li>
 *   <li>{@link #SKILL_POINTS} — skill point (trace) tree data by character ID</li>
 * </ul>
 *
 * <p>{@link #PERCENT_TO_BASE} maps percentage-type attributes to their corresponding
 * base attributes for modifier redirection during calculation.
 */
public final class Constant {
    /**
     * Relic main attribute value tables (keyed by star level 2-5).
     * <star level> part attribute <base bonus> <double value>
     */
    public static final RelicMainAttribute RELIC_MAIN_ATTRIBUTES;
    /**
     * Relic sub-attribute value tables (keyed by star level 2-5).
     */
    public static final RelicSubAttribute RELIC_SUB_ATTRIBUTES;
    /**
     * Weapon base data indexed by weapon ID.
     */
    public static final Map<Integer, WeaponData> WEAPONS;
    /**
     * Character base data indexed by character ID.
     */
    public static final Map<Integer, CharacterData> CHARACTERS;
    /**
     * Skill point tree data indexed by character ID.
     */
    public static final Map<Integer, List<SkillPoint>> SKILL_POINTS;

    public static final Map<Integer, Map<Integer, Skill>> SKILLS;

    /**
     * 怪物模板基础属性（{@code monster_template_config.json}）：template_id → 基础值。
     */
    public static final Map<Integer, MonsterTemplate> MONSTER_TEMPLATES;
    /**
     * 怪物实例数据（{@code monster_config.json}）：monster_id → 实例系数 / 弱点 / 抗性。
     * 装载时已由 {@link #normalizeMonsterConfigs} 补全缺失系数，下游拿到的一定非 null。
     */
    public static final Map<Integer, MonsterConfig> MONSTER_CONFIGS;
    /**
     * 等级组系数（{@code hard_level_group.json}）：组号 → 等级 → 系数。
     * 组号与等级来自关卡（StageConfig）；P2 阶段由调用方显式传入。
     */
    public static final Map<Integer, Map<Integer, HardLevelGroup>> HARD_LEVEL_GROUPS;

    /**
     * Maps percentage-type attributes to their corresponding base-type attributes.
     * E.g. HEALTH_PERCENT → HEALTH means health percentage bonuses are merged
     * into the HEALTH attribute's modifier list.
     */
    public static final Map<AttributeType, AttributeType> PERCENT_TO_BASE = Map.of(
            AttributeType.HEALTH_PERCENT, AttributeType.HEALTH,
            AttributeType.ATTACK_PERCENT, AttributeType.ATTACK,
            AttributeType.DEFENCE_PERCENT, AttributeType.DEFENCE,
            AttributeType.SPEED_PERCENT, AttributeType.SPEED
    );

    /**
     * Upper bound of the vulnerability zone multiplier (易伤区系数上限).
     */
    public static final double VULNERABLE_CAP = 3.5;
    /**
     * Lower bound of the damage-reduction zone multiplier (减伤区系数下限).
     */
    public static final double REDUCTION_MIN = 0.01;
    /**
     * Lower bound of the weakness zone multiplier (虚弱区系数下限).
     */
    public static final double WEAKNESS_MIN = 0.2;
    /**
     * Lower bound of the raw resistance value (-100%).
     */
    public static final double RESIST_MIN = -1.0;
    /**
     * Upper bound of the raw resistance value (90%).
     */
    public static final double RESIST_MAX = 0.9;
    /**
     * Level-independent term of the defence zone formula.
     */
    public static final double DEFENCE_CONST = 200.0;
    /**
     * Per-level term of the defence zone formula.
     */
    public static final double DEFENCE_PER_LEVEL = 10.0;
    /**
     * Whether true damage skips every damage zone (真伤跳过乘区开关).
     */
    public static final boolean TRUE_DMG_SKIP_ZONES = true;

    /**
     * 常规普攻的回能（P3 兜底值）。
     *
     * <p>取自 tbgd {@code AvatarSkillConfig.SPBase} 的常规档（ROADMAP P3-0 口径 2）：
     * 普攻 20 / 战技 30 / 终结技 5 是全角色通用值，**多段（弹射）技能的数据是「每段值」**
     * （艾丝妲/桑博/那刻夏/同谐开拓者 6×5、瓦尔特 10×3），总量仍是 30，别按段数再乘一次。
     * P3-4 把技能数据落库后，这里只作为「没有技能数据时」的兜底。
     */
    public static final double ENERGY_GAIN_BASIC = 20;
    /**
     * 常规战技的回能（P3 兜底值）。见 {@link #ENERGY_GAIN_BASIC}。
     */
    public static final double ENERGY_GAIN_SKILL = 30;
    /**
     * 常规终结技的回能（P3 兜底值）。终结技一律 5（饮月 3 段、米沙多段、银枝弹射 6 次都是 5），
     * 不做段数乘算；释放时先清零再回这 5 点。
     */
    public static final double ENERGY_GAIN_ULTRA = 5;
    /**
     * 受击回能基准（P3）。文档没给直接数值，由「娜塔莎星魂4 受到攻击后**额外**恢复 5 点」、
     * 「云璃受到攻击后**额外**恢复 15 点」反推存在基准值 10；等 P9 用数据校准。
     */
    public static final double ENERGY_GAIN_HIT = 10;
    /**
     * 击杀回能基准（P3，待校准）。文档里只以「额外恢复」形式出现。
     */
    public static final double ENERGY_GAIN_KILL = 5;
    /**
     * 击破回能基准（P3，待校准）。P4-4 击破时调用。
     */
    public static final double ENERGY_GAIN_BREAK = 5;

    /**
     * 击破基数表：等级 → 基数（P4-3）。**数据文件里是 10 倍值**，用的时候要 {@code /10}
     * （80 级 = 3767.5535 → 376.75535）。
     *
     * <p>见 {@code models/BreakDamageCalculator} 的单位说明：本项目削韧值统一用「点」刻度。
     */
    public static final Map<Integer, Double> BREAKING_RATE;

    /**
     * 击破推条比例（P4-4）：击破瞬间把目标行动条往后推 25%（单位 = 该目标的行动周期）。
     */
    public static final double BREAK_DELAY_RATIO = 0.25;

    /**
     * 击破持续回合数（P4-4）：敌人被击破后跳过这么多个自己的回合，然后韧性回满。
     */
    public static final int BROKEN_REMAIN_TURNS = 2;

    static {
        RELIC_MAIN_ATTRIBUTES = JSONReader.fromJSON("main_attribute.json", RelicMainAttribute.class);
        RELIC_SUB_ATTRIBUTES = JSONReader.fromJSON("sub_attribute.json", RelicSubAttribute.class);
        WEAPONS = JSONReader.fromJSON("weapons.json", new TypeToken<Map<Integer, WeaponData>>() {
        }.getType());
        CHARACTERS = JSONReader.fromJSON("character_data.json", new TypeToken<Map<Integer, CharacterData>>() {
        }.getType());
        SKILL_POINTS = JSONReader.fromJSON("point.json", new TypeToken<Map<Integer, List<SkillPoint>>>() {
        }.getType());
        SKILLS = JSONReader.fromJSON("skills.json", new TypeToken<Map<Integer, Map<Integer, Skill>>>() {
        }.getType());
        MONSTER_TEMPLATES = JSONReader.fromJSON("monster_template_config.json",
                new TypeToken<Map<Integer, MonsterTemplate>>() {
                }.getType());
        HARD_LEVEL_GROUPS = JSONReader.fromJSON("hard_level_group.json",
                new TypeToken<Map<Integer, Map<Integer, HardLevelGroup>>>() {
                }.getType());
        MONSTER_CONFIGS = normalizeMonsterConfigs(
                JSONReader.fromJSON("monster_config.json", new TypeToken<Map<Integer, MonsterConfig>>() {
                }.getType()),
                JSONReader.fromJSON("monster_attack_modify_ratio.json", new TypeToken<Map<Integer, Double>>() {
                }.getType()));
        BREAKING_RATE = JSONReader.fromJSON("breaking_rate.json", new TypeToken<Map<Integer, Double>>() {
        }.getType());
    }

    /**
     * 补全实例数据里缺的系数，让下游（EnemyScaler）永远拿到确定值：
     * <ul>
     *   <li><b>攻击修正</b>：本数据没导出 tbgd 的 {@code AttackModifyRatio}（2649 个怪里 444 个 ≠ 1），
     *   从补丁文件 {@code monster_attack_modify_ratio.json} 合并；表里没有的按 1.0。</li>
     *   <li>其余系数缺失时按 1.0（游戏语义 = 不修正）。</li>
     *   <li>{@code stance_weak} 缺失（有 102 个怪的条目没有这一项）→ 空列表；{@code damage_resistance} → 空表。</li>
     * </ul>
     */
    private static Map<Integer, MonsterConfig> normalizeMonsterConfigs(Map<Integer, MonsterConfig> raw,
                                                                      Map<Integer, Double> attackRatios) {
        Map<Integer, Double> patches = attackRatios == null ? Map.of() : attackRatios;
        Map<Integer, MonsterConfig> normalized = new LinkedHashMap<>();
        raw.forEach((id, config) -> normalized.put(id, new MonsterConfig(
                config.name(),
                config.templateId(),
                config.eliteGroup(),
                config.hardLevelGroup(),
                config.stanceWeak() == null ? List.of() : List.copyOf(config.stanceWeak()),
                orOne(config.hpRatio()),
                patches.getOrDefault(id, orOne(config.attackRatio())),
                orOne(config.defenceRatio()),
                orOne(config.speedRatio()),
                orOne(config.stanceRatio()),
                config.damageResistance() == null ? Map.of() : Map.copyOf(config.damageResistance()))));
        return Map.copyOf(normalized);
    }

    /**
     * 缺失的修正系数按 1.0（不修正）。
     */
    private static double orOne(Double value) {
        return value == null ? 1.0 : value;
    }
}
