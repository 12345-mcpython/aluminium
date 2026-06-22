package com.laosun.aluminium.enums;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * All character and equipment attribute types used in combat stat calculation.
 *
 * <p>Attributes fall into two categories:
 * <ul>
 *   <li><b>Base attributes</b> ({@code isPercent = false}): HEALTH, ATTACK, DEFENCE, SPEED —
 *   these have raw numeric values and can receive percentage-based bonuses via their
 *   corresponding PERCENT variants.</li>
 *   <li><b>Percent/rate attributes</b> ({@code isPercent = true}): all others — these
 *   represent ratios (e.g. CRIT_CHANCE is between 0 and 1) and are not affected by
 *   percentage-based bonus conversion.</li>
 * </ul>
 *
 * <p>In the internal attribute array built by {@link com.laosun.aluminium.utils.AttributeBuilder},
 * PERCENT-typed attributes are stored as {@code null} because their values are merged into
 * the corresponding base attribute's modifier list.
 */
@Getter
public enum AttributeType {
    @SerializedName("health") HEALTH("health", false),
    @SerializedName("defence") DEFENCE("defence", false),
    @SerializedName("attack") ATTACK("attack", false),
    @SerializedName("speed") SPEED("speed", false),
    // In the attribute calculate these are temporary value need be null.
    @SerializedName("health_percent") HEALTH_PERCENT("health_percent"),
    @SerializedName("defence_percent") DEFENCE_PERCENT("defence_percent"),
    @SerializedName("attack_percent") ATTACK_PERCENT("attack_percent"),
    @SerializedName("speed_percent") SPEED_PERCENT("speed_percent"),
    // end value

    @SerializedName("crit_chance") CRIT_CHANCE("crit_chance"),
    @SerializedName("crit_attack") CRIT_ATTACK("crit_attack"),
    @SerializedName("effect_hit_rate") EFFECT_HIT_RATE("effect_hit_rate"),
    @SerializedName("effect_resistance") EFFECT_RESISTANCE("effect_resistance"),

    @SerializedName("outgoing_healing_boost") OUTGOING_HEALING_BOOST("outgoing_healing_boost"),
    @SerializedName("heal_taken_ratio") HEAL_TAKEN_RATIO("heal_taken_ratio"),

    @SerializedName("breaking_effect") BREAKING_EFFECT("breaking_effect"),
    @SerializedName("energy_regeneration_rate") ENERGY_REGENERATION_RATE("energy_regeneration_rate"),

    @SerializedName("physical_damage_boost") PHYSICAL_DAMAGE_BOOST("physical_damage_boost"),
    @SerializedName("fire_damage_boost") FIRE_DAMAGE_BOOST("fire_damage_boost"),
    @SerializedName("ice_damage_boost") ICE_DAMAGE_BOOST("ice_damage_boost"),
    @SerializedName("thunder_damage_boost") THUNDER_DAMAGE_BOOST("thunder_damage_boost"),
    @SerializedName("wind_damage_boost") WIND_DAMAGE_BOOST("wind_damage_boost"),
    @SerializedName("quantum_damage_boost") QUANTUM_DAMAGE_BOOST("quantum_damage_boost"),
    @SerializedName("imaginary_damage_boost") IMAGINARY_DAMAGE_BOOST("imaginary_damage_boost"),

    @SerializedName("damage_penetration") DAMAGE_PENETRATION("damage_penetration"),
    @SerializedName("defence_ignore") DEFENCE_IGNORE("defence_ignore"),

    @SerializedName("all_damage_type_boost") ALL_DAMAGE_TYPE_BOOST("all_damage_type_boost"),

    @SerializedName("elation_damage_boost") ELATION_DAMAGE_BOOST("elation_damage_boost");

    /**
     * The string identifier used in JSON serialization and game data files.
     */
    public final String attributeString;

    /**
     * Whether this attribute represents a percentage/ratio rather than a raw value.
     * {@code true} for attributes like CRIT_CHANCE, DAMAGE_BOOST, etc.
     */
    public final boolean isPercent;

    private static final Map<String, AttributeType> BY_STRING = new HashMap<>();

    static {
        for (AttributeType type : values()) {
            BY_STRING.put(type.attributeString.toLowerCase(), type);
        }
    }

    AttributeType(String string) {
        this(string, true);
    }

    AttributeType(String string, boolean isPercent) {
        attributeString = string;
        this.isPercent = isPercent;
    }

    /**
     * Looks up an attribute type by its string identifier (case-insensitive).
     *
     * @param string the attribute string, e.g. "health", "crit_chance"
     * @return the matching {@code AttributeType}
     * @throws IllegalArgumentException if no match is found
     */
    public static AttributeType fromString(String string) {
        if (BY_STRING.containsKey(string)) {
            return BY_STRING.get(string);
        }
        throw new IllegalArgumentException("Unknown AttributeType: " + string);
    }
}
