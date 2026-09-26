package com.laosun.aluminium.enums;

import lombok.Getter;

import java.util.Map;

/**
 * Path (命途) (P5-1). The Path determines the **base aggro value**, and thereby the
 * probability that a single-target / blast enemy attack selects that character.
 *
 * <p>Aggro is "an absolute weight", not a percentage: hit probability =
 * {@code that character's aggro / the whole team's total aggro}.
 * The official tiers (<b>fully verified against the {@code aggro} column of
 * {@code character_data.json}</b>, every tier matches for all 93 characters):
 *
 * <pre>
 *   protection（存护）  150
 *   destruction（毁灭） 125
 *   all other Paths     100
 *   single（巡猎）/ all（智识） 75   ← lower than the standard tier, do not treat it as 100
 * </pre>
 *
 * <p>{@code mt} is the raw string from {@code character_data.json}; it has only 9 possible
 * values: {@code all / debuff / destruction / elation / healing / help / memory / protection / single}.
 * Any unlisted value falls through to {@link #OTHER} (= 100) with no fail fast — when the data
 * gains a new Path it should degrade rather than blow up.
 */
@Getter
public enum Path {
    /**
     * Preservation: aggro 150.
     */
    PRESERVATION("protection", 150),
    /**
     * Destruction: aggro 125.
     */
    DESTRUCTION("destruction", 125),
    /**
     * Hunt: aggro 75 (lower than the standard tier).
     */
    HUNT("single", 75),
    /**
     * Erudition: aggro 75.
     */
    ERUDITION("all", 75),
    /**
     * Harmony: aggro 100.
     */
    HARMONY("help", 100),
    /**
     * Nihility: aggro 100.
     */
    NIHILITY("debuff", 100),
    /**
     * Abundance: aggro 100.
     */
    ABUNDANCE("healing", 100),
    /**
     * Elation: aggro 100.
     */
    ELATION("elation", 100),
    /**
     * Remembrance: aggro 100.
     */
    REMEMBRANCE("memory", 100),
    /**
     * Fallback for an unknown / missing Path: aggro 100.
     */
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
     * The raw Path string on the data side (the {@code mt} of {@code character_data.json}).
     */
    private final String mt;
    /**
     * Base aggro value.
     */
    private final int aggro;

    Path(String mt, int aggro) {
        this.mt = mt;
        this.aggro = aggro;
    }

    /**
     * Look up a Path by the data side's {@code mt} (case sensitive; values are in the class
     * comment).
     *
     * @param mt the Path string; {@code null} or unlisted → {@link #OTHER}
     * @return the Path (never {@code null})
     */
    public static Path fromMt(String mt) {
        return mt == null ? OTHER : BY_MT.getOrDefault(mt, OTHER);
    }

    /**
     * Look up a Path by its Chinese name (for manual input such as "存护/毁灭").
     *
     * @param name the Chinese Path name; unlisted → {@link #OTHER}
     * @return the Path (never {@code null})
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
