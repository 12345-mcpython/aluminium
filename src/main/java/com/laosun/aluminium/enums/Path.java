package com.laosun.aluminium.enums;

import lombok.Getter;

import java.util.Map;

/**
 * Path (命途). The Path determines the base aggro value, and thereby the
 * probability that a single-target / blast enemy attack selects that character.
 *
 * <p>Aggro is "an absolute weight", not a percentage: hit probability =
 * {@code that character's aggro / the whole team's total aggro}.
 * The official tiers (<b>fully verified against the {@code aggro} column of
 * {@code character_data.json}</b>, every tier matches for all 93 characters):
 *
 * <pre>
 *   protection (存护)  150
 *   destruction (毁灭) 125
 *   all other Paths     100
 *   single (巡猎) / all (智识) 5 - lower than the standard tier, do not treat it as 100
 * </pre>
 *
 * <p>{@code mt} is the raw string from {@code character_data.json}; it has only 9 possible
 * values: {@code all / debuff / destruction / elation / healing / help / memory / protection / single}.
 * Any unlisted value falls through to {@link #OTHER} (= 100) with no fail fast - when the data
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
     * Hunt: aggro 5 (lower than the standard tier).
     */
    HUNT("single", 75),
    /**
     * Erudition: aggro 5.
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
     * The nine Paths by their <b>Chinese</b> name - the spelling a rule file writes
     * ({@code target has_path 同谐} (Harmony)).
     *
     * <p>Kept here rather than in the condition DSL for the same reason {@link #BY_MT} is: "a Path name means
     * this Path" is one fact, and the aggro table and the DSL must not be able to disagree about it.
     */
    private static final Map<String, Path> BY_NAME = Map.ofEntries(
            Map.entry("存护", PRESERVATION),
            Map.entry("毁灭", DESTRUCTION),
            Map.entry("巡猎", HUNT),
            Map.entry("智识", ERUDITION),
            Map.entry("同谐", HARMONY),
            Map.entry("虚无", NIHILITY),
            Map.entry("丰饶", ABUNDANCE),
            Map.entry("欢愉", ELATION),
            Map.entry("记忆", REMEMBRANCE));

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
     * @param mt the Path string; {@code null} or unlisted to {@link #OTHER}
     * @return the Path (never {@code null})
     */
    public static Path fromMt(String mt) {
        return mt == null ? OTHER : BY_MT.getOrDefault(mt, OTHER);
    }

    /**
     * Look up a Path by its Chinese name (for manual input such as "存护/毁灭", i.e. "Preservation/Destruction").
     *
     * @param name the Chinese Path name; unlisted to {@link #OTHER}
     * @return the Path (never {@code null})
     */
    public static Path fromName(String name) {
        Path path = fromNameOrNull(name);
        return path == null ? OTHER : path;
    }

    /**
     * The same lookup, but an unknown name answers {@code null} instead of {@link #OTHER}.
     *
     * <p>Both answers are needed and they are not interchangeable. {@link #fromName} is for data that may
     * legitimately carry a Path this build does not know (it must degrade to the 100 aggro tier rather than
     * blow up). A <b>rule file</b> is different: {@code target has_path 同谐} (Harmony) with a typo would otherwise
     * become "the target is on some other Path", i.e. a condition that quietly means something else - so the
     * condition DSL refuses it at load time, and it needs a lookup that can say "not a Path".
     *
     * @param name the Chinese Path name
     * @return the Path, or {@code null} when the name is not one of the nine
     */
    public static Path fromNameOrNull(String name) {
        return name == null ? null : BY_NAME.get(name);
    }

    /**
     * The Chinese names of the nine Paths, for error messages that list the vocabulary.
     */
    public static java.util.Set<String> names() {
        return BY_NAME.keySet();
    }
}
