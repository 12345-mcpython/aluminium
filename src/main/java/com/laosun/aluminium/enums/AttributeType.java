package com.laosun.aluminium.enums;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

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

    public final String attributeString;

    public final boolean isPercent;

    AttributeType(String string) {
        this(string, true);
    }

    AttributeType(String string, boolean isPercent) {
        attributeString = string;
        this.isPercent = isPercent;
    }

    public static AttributeType fromString(String string) {
        for (AttributeType attributeType : AttributeType.values()) {
            if (attributeType.attributeString.equalsIgnoreCase(string)) {
                return attributeType;
            }
        }
        throw new IllegalArgumentException("Unknown AttributeType: " + string);
    }
}
