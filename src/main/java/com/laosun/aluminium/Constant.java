package com.laosun.aluminium;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.*;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.utils.JSONReader;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
     * 敌人技能表（{@code enemy_skills.json}，P5-3 自建）：{@code 怪物实例 id → 技能}。
     *
     * <p>⚠ 这张表的**倍率是猜的**（数据源里没有敌人技能表），每条数据带 {@code guessed} 标记。
     * 见 {@link com.laosun.aluminium.beans.EnemySkillData}。
     */
    public static final Map<Integer, EnemySkillData> ENEMY_SKILLS;

    /**
     * 关卡表（{@code stage.json}）：{@code stage_id → }{@link StageBean}。**懒加载**。
     *
     * <p>为什么不像其它数据表那样塞进静态块：{@code stage.json} 有 9 MB / 约 2.9 万条关卡，
     * 比其它所有数据加起来还大，而绝大多数测试和 demo 根本不碰关卡。
     * 放进静态块等于让每次 {@code Constant} 初始化都多付 ~35 MB 堆 + 几十毫秒。
     *
     * <p>⚠ 与其它数据表的第二点差别：{@code stage.json} 缺失时这里返回**空表**而不是抛异常。
     * 它只服务关卡驱动（P7-4/P7-5），而 `Constant` 的静态块是"碰一下就整个测试套件一起挂"
     * 的地方 —— 一个可选功能不该把全套测试拖下水。取不到关卡时由调用方
     * （{@code StageFactory.load}）给出自解释的报错。
     *
     * @return 关卡表；数据文件缺失时为空表
     */
    public static Map<Integer, StageBean> stages() {
        return StageHolder.LOADED;
    }

    /**
     * 关卡表被**解析过几次**（0 或 1）—— 仅供测试观测懒加载（P7-4）。
     *
     * <p>为什么需要它：Java 没有公开 API 能查询"某个类是否已初始化"而不触发初始化，
     * 所以"没人调 {@link #stages()} 就不该读 stage.json"这件事在测试里需要一个可观测点。
     * 计入的是**解析尝试**（文件缺失导致的失败也算）—— 那正是要推迟的工作。
     *
     * <p>刻意放在**独立的类**里，不放进 {@link StageHolder}：{@code StageHolder} 的静态字段
     * 按声明顺序初始化，把计数器放在被调用者后面会读到默认值 0。
     */
    public static int stageLoadAttempts() {
        return StageProbe.LOAD_ATTEMPTS.get();
    }

    /**
     * 见 {@link #stageLoadAttempts()}：与 {@link StageHolder} 分开的计数器，
     * 避免静态字段初始化顺序把计数读成 0。
     */
    private static final class StageProbe {
        private static final AtomicInteger LOAD_ATTEMPTS = new AtomicInteger();
    }

    /**
     * 关卡表的懒加载载体。
     *
     * <p>关键在 {@code LOADED} 是 {@link StageHolder} 的静态字段：**嵌套类在首次被引用时**
     * 才初始化，所以 {@code Constant} 的静态块跑完也不会解析 {@code stage.json}，
     * 直到有人真的调 {@link #stages()}。
     */
    private static final class StageHolder {
        private static final Map<Integer, StageBean> LOADED = load();

        private static Map<Integer, StageBean> load() {
            StageProbe.LOAD_ATTEMPTS.incrementAndGet();
            try {
                return Map.copyOf(JSONReader.fromJSON("stage.json",
                        new TypeToken<Map<Integer, StageBean>>() {
                        }.getType()));
            } catch (IllegalStateException e) {
                // 数据没生成 → 空表。真要用关卡的人会在 StageFactory.load 拿到明确的报错。
                return Map.of();


            }
        }
    }


    /**
     * {@link SkillType} → {@code skills.json} 里的**技能槽位号**（P8-2）。
     *
     * <p>数据的槽位约定：<b>1 普攻 / 2 战技 / 3 终结技 / 4 天赋 / 5（无）/ 6 地图普攻 / 7 秘技</b>，
     * 且 {@code skill_id = 角色id × 100 + 槽位}（638 条技能**全部**满足，已核对）。
     *
     * <p>⚠ 为什么只有这 4 项：{@link SkillType} 里没有地图普攻/秘技对应的枚举值，
     * 所以槽位 6/7 无法映射 —— 想覆盖它们得先加枚举值（见 ROADMAP P8-2 的偏差记录）。
     *
     * <p>⚠ 这张表必须**只有一份**：修之前 {@code Character.Builder.build()} 把每个槽位
     * 都写成 {@code new DefaultSkill(cid, 1, level)}，于是普攻/战技/终结技/天赋**全部**解析到槽位 1，
     * 后果是六个槽位的倍率、削韧、元素、{@code sp_need} 全是普攻的。
     */
    public static final Map<SkillType, Integer> SKILL_SLOT = Map.of(
            SkillType.COMMON, 1,
            SkillType.SKILL, 2,
            SkillType.ULTRA, 3,
            SkillType.TALENT, 4);

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

    /**
     * 超击破独立增伤（P4-6）：{@code 1 + SUPER_BREAK_BOOST} 乘进超击破伤害。
     *
     * <p>与常规增伤区**无关**——超击破不吃属性/攻击类型增伤（由 {@code DamageType.SUPER_BREAK}
     * 的 {@code isBoostable() == false} 挡掉），所以它是一个**独立乘区**，只能从这里取。
     *
     * <p>**示例值，TODO data**：文档只写"2.2 版本仅开拓者·同谐提供"（其行迹按场上敌人数给
     * 20%~60%），没有可查的数值表，先用 0.4 占位。
     */
    public static final double SUPER_BREAK_BOOST = 0.4;

    /**
     * 击破 DOT 每次结算的基础伤害 = 击破基数 × 本比例（**示例值，TODO data**：
     * HSR.md §2 只写"基础倍率由等级与击破特攻决定（查数值表）"，逐元素倍率还没拿到）。
     */
    public static final double DOT_RATIO = 0.5;

    /**
     * 击破 DOT 持续结算次数（**示例值，TODO data**）。
     */
    public static final int DOT_TURNS = 3;

    /**
     * 会附带持续伤害的击破元素：火=灼烧、雷=触电、物理=裂伤、风=风化（GLOSSARY_EXTRA 10000012）。
     * 冰=冻结、量子=纠缠、虚数=禁锢，属控制类击破效果 → P10-1 统一成表。
     */
    public static final Set<DamageElement> DOT_ELEMENTS =
            EnumSet.of(DamageElement.FIRE, DamageElement.THUNDER, DamageElement.PHYSICAL, DamageElement.WIND);

    /**
     * 一轮的行动值（P7-1）：后续每轮 **100**。
     *
     * <p>本项目里"行动值（Action Value, AV）"是**时间量纲**：速度 100 的单位一个周期走
     * 100 行动值，所以 {@link com.laosun.aluminium.Queue#move()} 推进的 {@code elapsed}
     * 就是累计行动值，{@link com.laosun.aluminium.Queue#getRound()} 直接拿它分轮。
     */
    public static final double ROUND_ACTION_VALUE = 100;

    /**
     * 首轮行动值倍率（P7-1）：首轮总行动值 **150**，之后每轮 **100**。
     *
     * <p>所以速度 100 的单位首轮要等 150 才动，第二圈起每 100 动一次；速度 200 的单位
     * 首轮等 75。这不是"首轮整体延后"，而是每个单位的**第一个周期**被拉长 1.5 倍 ——
     * 首轮里高速单位能多动几次（速度 240 的周期 41.67，首轮 150 之内能动 3 次）。
     *
     * <p>⚠ 只有 {@link com.laosun.aluminium.Queue#initialize()}（战斗开场）施加这个系数；
     * {@code setTopZero()} / {@code addCombatant()} 之后都按正常周期排队。
     * 中途变速时靠 {@link com.laosun.aluminium.models.Signal#isFirstRound()} 记账保留它。
     */
    public static final double FIRST_ROUND_MULTIPLIER = 1.5;

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
        // enemy_skills.json 顶层是 { "_comment": [...], "skills": {怪物id: {...}} }，
        // 用一个内联 record 只取 skills（Gson 会忽略未声明的 _comment）。
        EnemySkillsFile enemySkills = JSONReader.fromJSON("enemy_skills.json", EnemySkillsFile.class);
        ENEMY_SKILLS = Map.copyOf(enemySkills.skills());
    }

    /**
     * {@code enemy_skills.json} 的顶层结构（只为跳过 {@code _comment}）。
     */
    private record EnemySkillsFile(Map<Integer, EnemySkillData> skills) {
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
                config.damageResistance() == null ? Map.of() : Map.copyOf(config.damageResistance()),
                config.debuffResistance() == null ? Map.of() : Map.copyOf(config.debuffResistance()))));
        return Map.copyOf(normalized);
    }

    /**
     * 缺失的修正系数按 1.0（不修正）。
     */
    private static double orOne(Double value) {
        return value == null ? 1.0 : value;
    }
}
