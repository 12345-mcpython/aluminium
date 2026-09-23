package com.laosun.aluminium.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * 技能在**数据里的** {@code attack_type}（{@code skills.json} 的原始值）。
 *
 * <p>⚠ <b>命名</b>：本枚举**刻意不叫** {@code SkillAttackType} —— 那个名字已被
 * {@link SkillAttackType}（目标形状：单体/扩散/群攻）占用，是**另一个轴**。
 * 本枚举是"这是什么档次的技能"，与 {@link SkillEffectType}（技能干了什么）也正交。
 *
 * <p><b>为什么需要这个枚举</b>：数据把 {@code attack_type} 存成裸字符串，而引擎里
 * 有两处要按它分支（战技点结算、技能回能）。用字符串 {@code switch} 的问题是
 * **数据侧改拼写或新增类型时会静默失配** —— 落到 {@code default} 分支被吞掉，
 * 既没有编译期保护，也没有运行期报错。集中到这个枚举后，"数据值 → 引擎语义"
 * 只有一处定义，新增类型时编译器会逼着每个 {@code switch} 表态。
 *
 * <p>⚠ <b>与 {@link SkillType} 不是一套东西</b>，别互相替换：
 * <ul>
 *   <li>{@code SkillType} 是**槽位类别**（角色身上装了哪个槽），含 {@code SUMMON_SKILL} /
 *       {@code SUMMON_TALENT} 两个数据里不存在的值；</li>
 *   <li>本枚举是**数据里的攻击类型**，含 {@code ASSIST} / {@code ELATION_DAMAGE}
 *       两个 {@code SkillType} 里没有的值。</li>
 * </ul>
 * 两者的交集只有 {@code Normal / BPSkill / Ultra / Maze / MazeNormal} 那五个。
 *
 * <p>数据实测（638 条技能，{@code skills.json}）：
 * {@code Normal} 122、{@code Ultra} 114、{@code BPSkill} 109、{@code MazeNormal} 94、
 * {@code Maze} 93、{@code null} 94（天赋与追加攻击）、{@code ElationDamage} 9、
 * {@code Assist} 3。
 */
public enum SkillCategory {
    /** 战斗内普攻（数据 {@code "Normal"}）。 */
    NORMAL("Normal"),
    /** 战技（数据 {@code "BPSkill"}）。 */
    BPSKILL("BPSkill"),
    /** 终结技（数据 {@code "Ultra"}）。 */
    ULTRA("Ultra"),
    /** 地图普攻（数据 {@code "MazeNormal"}）：**战斗外**用的那一击。 */
    MAZE_NORMAL("MazeNormal"),
    /** 秘技（数据 {@code "Maze"}）：战斗外主动施放。 */
    MAZE("Maze"),
    /** 助战技（数据 {@code "Assist"}，实测 3 条）。 */
    ASSIST("Assist"),
    /** 欢愉伤害技能（数据 {@code "ElationDamage"}，实测 9 条，P10 欢愉体系）。 */
    ELATION_DAMAGE("ElationDamage"),
    /**
     * 数据里 {@code attack_type} 为空 —— 实测 94 条，都是**天赋与追加攻击**
     * （它们不是"主动出手"，所以没有攻击类型）。
     */
    UNSPECIFIED(""),
    /**
     * 数据里出现了本项目还不认识的取值。
     *
     * <p>刻意**不抛异常**：数据是外部产物，多一个新类型就炸引擎是稳定性问题。
     * 这里选择"安全降级 + 可观测"，由 {@link #isKnownValue()} 让调用方决定要不要出声。
     */
    UNKNOWN("");

    /**
     * 本枚举承认的**全部**数据取值（含 {@code "ElationDamage"} 这种大小写混写）。
     *
     * <p>唯一真源：{@link #fromString} 查它，{@link #isKnownValue()} 也查它。
     *
     * <p>键一律经 {@link #normalize} 归一化 —— 否则"大小写不敏感"就只是
     * javadoc 里的一句空话（第一版就是这么错的：键存原样，查表用小写，永远查不到）。
     */
    private static final Map<String, SkillCategory> BY_VALUE = new HashMap<>();

    static {
        for (SkillCategory category : values()) {
            if (category.isKnownValue()) {
                BY_VALUE.put(normalize(category.value), category);
            }
        }
    }

    /**
     * 取值归一化：去首尾空白 + 转小写。建表与查表**必须**走同一个函数。
     */
    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private final String value;

    SkillCategory(String value) {
        this.value = value;
    }

    /**
     * 数据里的原始字符串（{@code UNSPECIFIED} / {@code UNKNOWN} 为空串）。
     */
    public String value() {
        return value;
    }

    /**
     * 这个方法是不是 {@code null} 的替代品 —— 即数据里**本来就没有**攻击类型。
     *
     * <p>用于"天赋/追加攻击"这类分支：它们是合法的空，不是数据错误。
     */
    public boolean isUnspecified() {
        return this == UNSPECIFIED;
    }

    /**
     * 这个值是不是数据里**真实存在**的合法取值。
     *
     * <p>{@code false} 表示两类之一：{@link #UNSPECIFIED}（合法空）或
     * {@link #UNKNOWN}（引擎不认识的数据）。数据校验/诊断时用它。
     */
    public boolean isKnownValue() {
        return this != UNSPECIFIED && this != UNKNOWN;
    }

    /**
     * 这个类型算不算**战斗内的一次主动出手**。
     *
     * <p>{@code true}：普攻 / 战技 / 终结技。{@code false}：地图普攻、秘技
     * （都在战斗外）、助战技、欢愉伤害、天赋与追加攻击（数据里为空）。
     *
     * <p>注意**不要**拿它当"要不要结算战技点"的判据 —— 战技点的规则是
     * "普攻 +1 / 战技 -1 / 其余中性"，由
     * {@link com.laosun.aluminium.models.skillpoint.SkillPointPolicy} 表达。
     */
    public boolean isCombatAction() {
        return this == NORMAL || this == BPSKILL || this == ULTRA;
    }

    /**
     * 把数据里的字符串解析成枚举，**大小写不敏感**且会去掉首尾空白。
     *
     * <p>解析规则：
     * <ul>
     *   <li>{@code null} 或空串 → {@link #UNSPECIFIED}（数据里的合法空）；</li>
     *   <li>认识的取值 → 对应枚举；</li>
     *   <li>不认识的取值 → {@link #UNKNOWN}（**不抛异常**，见该值说明）。</li>
     * </ul>
     *
     * @param raw 数据里的 {@code attack_type}
     * @return 永远非 {@code null}
     */
    public static SkillCategory fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNSPECIFIED;
        }
        SkillCategory category = BY_VALUE.get(normalize(raw));
        return category == null ? UNKNOWN : category;
    }
}
