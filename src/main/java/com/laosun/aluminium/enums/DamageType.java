package com.laosun.aluminium.enums;

import lombok.Getter;

import java.util.Locale;
import java.util.Map;

/**
 * The damage type of one damage instance (basic attack / skill / ultimate / break / DOT / true damage…).
 *
 * <p>Two game rules live on the type itself as data, so callers never have to
 * remember them:
 * <ul>
 *   <li>{@link #isCrittable()} — break / super break / DOT / true damage never crit
 *   (break, super break, DOT and true damage get no crit stats); elation damage does get crit stats
 *   (HSR.md §6.4).</li>
 *   <li>{@link #isBoostable()} — break / super break / true damage are not boosted by
 *   the damage-bonus zone (break and true damage get no DMG boost), and elation damage is likewise
 *   unaffected by damage-increasing effects
 *   (HSR.md §6.5 / GLOSSARY).</li>
 * </ul>
 *
 * <p>{@link com.laosun.aluminium.models.Damage.Area#applies(DamageType)} consumes
 * both flags, so a zone removes itself instead of relying on callers not to add it.
 *
 * <h2>Which values are actually used ⚠</h2>
 *
 * <p>These enum values are **laid out to spec**, but the engine currently only uses some of them.
 * Do not assume that being declared means being wired up:
 *
 * <table>
 *   <tr><th>Type</th><th>Status</th></tr>
 *   <tr><td>{@link #NORMAL}</td><td>✅ The **only** actual outlet of character skills — the data has
 *       no {@code damage_type} field (see the keys of {@code skills.json}), so skills/ultimates are
 *       recorded as {@code NORMAL} too.
 *       ⚠ There is currently **no behavioral difference**: the crittable and boostable flags of
 *       {@code NORMAL}/{@code SKILL}/{@code ULTRA} are identical, so for now it does not affect the
 *       numbers</td></tr>
 *   <tr><td>{@link #ADDITIONAL} / {@link #TRUE}</td><td>✅ Additional damage / true damage (P1-9)</td></tr>
 *   <tr><td>{@link #BREAK} / {@link #SUPER_BREAK} / {@link #DOT}</td><td>✅ Break / super break / DOT (P4)</td></tr>
 *   <tr><td>{@link #SKILL} / {@link #ULTRA}</td><td>❌ **0 references** — cannot be distinguished
 *       until the skill data gains a {@code damage_type} (everything is {@code NORMAL} for now)</td></tr>
 *   <tr><td>{@link #EXTRA}</td><td>❌ **0 references** — the "extra damage" in the spec, with no source</td></tr>
 *   <tr><td>{@link #TECHNIQUE}</td><td>❌ **0 references** — technique damage; the technique itself is
 *       currently only attached in {@code Battle.startBattle()} (P8-2) and its effect is not
 *       implemented (P8-6)</td></tr>
 *   <tr><td>{@link #MEMORY}</td><td>❌ **0 references** — memosprite damage, waiting on P9-4 summons</td></tr>
 *   <tr><td>{@link #ELATION}</td><td>❌ **0 references** — the elation system (P10); not even
 *       {@code elation_basic_level_damage.json} has been loaded yet</td></tr>
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
