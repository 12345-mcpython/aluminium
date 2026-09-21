package com.laosun.aluminium.enums;

import lombok.Getter;

import java.util.Map;

/**
 * 命途（P5-1）。命途决定**基础仇恨值**，进而决定敌人单体/扩散攻击选中该角色的概率。
 *
 * <p>仇恨值是"权重的绝对值"，不是百分比：受击概率 = {@code 该角色仇恨 / 全队总仇恨}。
 * 官方档位（<b>已用 {@code character_data.json} 的 {@code aggro} 列全量核对过</b>，
 * 93 个角色里每个档位都对得上）：
 *
 * <pre>
 *   protection（存护）  150
 *   destruction（毁灭） 125
 *   其余命途            100
 *   single（巡猎）/ all（智识） 75   ← 比常规还低，别当成 100
 * </pre>
 *
 * <p>{@code mt} 是 {@code character_data.json} 里的原始字符串，全部取值只有 9 个：
 * {@code all / debuff / destruction / elation / healing / help / memory / protection / single}。
 * 未列出的值一律落到 {@link #OTHER}（= 100），不做 fail fast —— 数据加新命途时应该降级而不是炸。
 */
@Getter
public enum Path {
    /** 存护：仇恨 150。 */
    PRESERVATION("protection", 150),
    /** 毁灭：仇恨 125。 */
    DESTRUCTION("destruction", 125),
    /** 巡猎：仇恨 75（比常规低）。 */
    HUNT("single", 75),
    /** 智识：仇恨 75。 */
    ERUDITION("all", 75),
    /** 同谐：仇恨 100。 */
    HARMONY("help", 100),
    /** 虚无：仇恨 100。 */
    NIHILITY("debuff", 100),
    /** 丰饶：仇恨 100。 */
    ABUNDANCE("healing", 100),
    /** 欢愉：仇恨 100。 */
    ELATION("elation", 100),
    /** 记忆：仇恨 100。 */
    REMEMBRANCE("memory", 100),
    /** 未知/缺失命途的兜底：仇恨 100。 */
    OTHER("", 100);

    private static final Map<String, Path> BY_MT = Map.ofEntries(
            Map.entry("protection", PRESERVATION),
            Map.entry("destruction", DESTRUCTION),
            Map.entry("single", HUNT),
            Map.entry("all", ERUDITION),
            Map.entry("help", HARMONY),
            Map.entry("debuff", NIHILITY),
            Map.entry("healing", ABUNDANCE),
            Map.entry("elation", ELATION),
            Map.entry("memory", REMEMBRANCE));

    /**
     * 数据侧的原始命途字符串（{@code character_data.json} 的 {@code mt}）。
     */
    private final String mt;
    /**
     * 基础仇恨值。
     */
    private final int aggro;

    Path(String mt, int aggro) {
        this.mt = mt;
        this.aggro = aggro;
    }

    /**
     * 按数据侧的 {@code mt} 查命途（大小写敏感，取值见类注释）。
     *
     * @param mt 命途字符串；{@code null} 或未收录 → {@link #OTHER}
     * @return 命途（永不返回 {@code null}）
     */
    public static Path fromMt(String mt) {
        return mt == null ? OTHER : BY_MT.getOrDefault(mt, OTHER);
    }

    /**
     * 按中文名查命途（给"存护/毁灭"这类人工输入用）。
     *
     * @param name 中文命途名；未收录 → {@link #OTHER}
     * @return 命途（永不返回 {@code null}）
     */
    public static Path fromName(String name) {
        return switch (name == null ? "" : name) {
            case "存护" -> PRESERVATION;
            case "毁灭" -> DESTRUCTION;
            case "巡猎" -> HUNT;
            case "智识" -> ERUDITION;
            case "同谐" -> HARMONY;
            case "虚无" -> NIHILITY;
            case "丰饶" -> ABUNDANCE;
            case "欢愉" -> ELATION;
            case "记忆" -> REMEMBRANCE;
            default -> OTHER;
        };
    }
}
