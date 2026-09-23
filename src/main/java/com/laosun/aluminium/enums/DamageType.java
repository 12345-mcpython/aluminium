package com.laosun.aluminium.enums;

import lombok.Getter;

import java.util.Locale;
import java.util.Map;

/**
 * The damage type of one damage instance (普攻/战技/终结技/击破/持续/真伤…).
 *
 * <p>Two game rules live on the type itself as data, so callers never have to
 * remember them:
 * <ul>
 *   <li>{@link #isCrittable()} — break / super break / DOT / true damage never crit
 *   （击破、超击破、持续伤害、真实伤害不吃双暴）；欢愉伤害吃双爆（HSR.md §6.4）。</li>
 *   <li>{@link #isBoostable()} — break / super break / true damage are not boosted by
 *   the damage-bonus zone（击破与真伤不吃增伤），欢愉伤害同样不受伤害提高类效果影响
 *   （HSR.md §6.5 / GLOSSARY）。</li>
 * </ul>
 *
 * <p>{@link com.laosun.aluminium.models.Damage.Area#applies(DamageType)} consumes
 * both flags, so a zone removes itself instead of relying on callers not to add it.
 *
 * <h2>哪些值真的在用 ⚠</h2>
 *
 * <p>这些枚举值是**照规格铺的**，但引擎目前只用到一部分。别以为声明了就等于接上了：
 *
 * <table>
 *   <tr><th>类型</th><th>现状</th></tr>
 *   <tr><td>{@link #NORMAL}</td><td>✅ 角色技能的**唯一**实际出口 —— 数据里没有
 *       {@code damage_type} 字段（见 {@code skills.json} 的键），所以战技/终结技也记 {@code NORMAL}。
 *       ⚠ 目前**没有行为差异**：{@code NORMAL}/{@code SKILL}/{@code ULTRA} 的
 *       可暴击与可增伤标志相同，所以暂时不影响数值</td></tr>
 *   <tr><td>{@link #ADDITIONAL} / {@link #TRUE}</td><td>✅ 附加伤害 / 真伤（P1-9）</td></tr>
 *   <tr><td>{@link #BREAK} / {@link #SUPER_BREAK} / {@link #DOT}</td><td>✅ 击破 / 超击破 / 持续伤害（P4）</td></tr>
 *   <tr><td>{@link #SKILL} / {@link #ULTRA}</td><td>❌ **引用 0 处** —— 等技能数据补上
 *       {@code damage_type} 才能区分（目前一律 {@code NORMAL}）</td></tr>
 *   <tr><td>{@link #EXTRA}</td><td>❌ **引用 0 处** —— 规格里的"额外伤害"，无来源</td></tr>
 *   <tr><td>{@link #TECHNIQUE}</td><td>❌ **引用 0 处** —— 秘技伤害；秘技本身现在只在
 *       {@code Battle.startBattle()} 被挂上（P8-2），效果未实现（P8-6）</td></tr>
 *   <tr><td>{@link #MEMORY}</td><td>❌ **引用 0 处** —— 忆灵伤害，要等 P9-4 召唤物</td></tr>
 *   <tr><td>{@link #ELATION}</td><td>❌ **引用 0 处** —— 欢愉体系（P10）；连
 *       {@code elation_basic_level_damage.json} 都还没加载</td></tr>
 * </table>
 */
@Getter
public enum DamageType {
    NORMAL("normal", true, true), SKILL("skill", true, true),
    ULTRA("ultra", true, true), ADDITIONAL("additional", true, true),
    BREAK("break", false, false), SUPER_BREAK("super_break", false, false),
    DOT("dot", false, true), EXTRA("extra", true, true),
    // Maze Skill
    TECHNIQUE("technique", true, true), MEMORY("memory", true, true),
    ELATION("elation", true, false), TRUE("true", false, false);

    private final String name;
    private final boolean isCrittable;
    /**
     * Whether the damage-bonus zone applies to this type.
     */
    private final boolean isBoostable;

    private static final Map<String, DamageType> MP = Map.ofEntries(
            Map.entry("normal", NORMAL),
            Map.entry("skill", SKILL),
            Map.entry("ultra", ULTRA),
            Map.entry("additional", ADDITIONAL),
            Map.entry("break", BREAK),
            Map.entry("super_break", SUPER_BREAK),
            Map.entry("dot", DOT),
            Map.entry("extra", EXTRA),
            // Maze Skill
            Map.entry("technique", TECHNIQUE),
            Map.entry("memory", MEMORY),
            Map.entry("elation", ELATION),
            Map.entry("true", TRUE)
    );

    DamageType(String st, boolean crittable, boolean boostable) {
        isCrittable = crittable;
        isBoostable = boostable;
        name = st;
    }

    /**
     * Looks up a damage type by its {@link #name} string, case-insensitive.
     *
     * @param string the damage type name, e.g. {@code "break"}, {@code "SUPER_BREAK"}
     * @return the matching damage type
     * @throws IllegalArgumentException if no match is found
     */
    public static DamageType fromString(String string) {
        DamageType type = string == null ? null : MP.get(string.toLowerCase(Locale.ROOT));
        if (type == null) {
            throw new IllegalArgumentException("Unknown DamageType: " + string);
        }
        return type;
    }
}
