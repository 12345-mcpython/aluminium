package com.laosun.aluminium.enums;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The damage element of a skill or attack, mapped from the {@code element} field
 * in {@code skills.json} (e.g. {@code "Fire"}, {@code "Ice"}).
 *
 * <p>The same strings also appear in {@code monster_config.json} under
 * {@code stance_weak} (enemy weaknesses) and the keys of {@code damage_resistance},
 * so this enum is the single source of truth for element names.
 *
 * <p><b>No-element skills:</b> non-damaging skills (heal / support / summon / ...)
 * carry the value {@code "Unknown"} in the game data. Parsing such a value is
 * <em>not</em> a failure and is not mapped to any element: {@link #fromString} simply
 * returns {@code null}, signalling "this skill has no damage element". Only
 * {@link com.laosun.aluminium.models.Damage Damage} combat objects carry an element,
 * and a skill that deals damage must never fall back to {@code null}.
 *
 * @see com.laosun.aluminium.models.SkillData#getElement()
 */
public enum DamageElement {
    /**
     * Physical element. Plain weapon strikes without a special element.
     */
    @SerializedName("Physical") PHYSICAL("Physical"),
    /**
     * Fire element.
     */
    @SerializedName("Fire") FIRE("Fire"),
    /**
     * Ice element.
     */
    @SerializedName("Ice") ICE("Ice"),
    /**
     * Thunder (lightning) element.
     */
    @SerializedName("Thunder") THUNDER("Thunder"),
    /**
     * Wind element.
     */
    @SerializedName("Wind") WIND("Wind"),
    /**
     * Quantum element.
     */
    @SerializedName("Quantum") QUANTUM("Quantum"),
    /**
     * Imaginary element.
     */
    @SerializedName("Imaginary") IMAGINARY("Imaginary");

    /**
     * The raw element string used in game data files, e.g. {@code "Fire"}.
     */
    @Getter
    private final String elementName;

    private static final Map<String, DamageElement> MP = Map.ofEntries(
            Map.entry("Physical", PHYSICAL),
            Map.entry("Fire", FIRE),
            Map.entry("Ice", ICE),
            Map.entry("Thunder", THUNDER),
            Map.entry("Wind", WIND),
            Map.entry("Quantum", QUANTUM),
            Map.entry("Imaginary", IMAGINARY)
    );

    /**
     * An element table indexed by **lowercase** key, so {@link #fromString} can look up
     * case-insensitively (P8-1).
     *
     * <p>Why it is needed: {@code skills.json} writes {@code "Thunder"} (capitalised first
     * letter), while the {@code attribute} of {@code character_data.json} writes
     * {@code "thunder"} (all lowercase). The two data files use inconsistent conventions, so
     * parsing MUST accept both.
     */
    private static final Map<String, DamageElement> BY_LOWER_NAME = MP.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                    entry -> entry.getKey().toLowerCase(Locale.ROOT),
                    Map.Entry::getValue));

    DamageElement(String name) {
        this.elementName = name;
    }

    /**
     * Looks up an element by its raw game-data string (e.g. {@code "Fire"}).
     *
     * <p><b>Case insensitive</b> (P8-1): {@code skills.json} uses {@code "Thunder"},
     * {@code character_data.json} uses {@code "thunder"} — both spellings occur in the same
     * data set. Leading and trailing whitespace is ignored as well.
     *
     * @param sp the raw element value, e.g. from the {@code element} field of a skill
     * @return the matching element, or {@code null} for {@code "Unknown"} /
     *         unparsable values (non-damaging skills)
     */
    public static DamageElement fromString(String sp) {
        return sp == null ? null : BY_LOWER_NAME.get(sp.strip().toLowerCase(Locale.ROOT));
    }
}
