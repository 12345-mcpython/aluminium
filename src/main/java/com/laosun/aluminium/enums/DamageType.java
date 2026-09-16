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
