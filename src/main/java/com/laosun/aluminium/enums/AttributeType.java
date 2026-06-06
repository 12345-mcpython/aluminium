package com.laosun.aluminium.enums;

import lombok.Getter;

@Getter
public enum AttributeType {
    HEALTH("health", false),
    DEFENCE("defence", false),
    ATTACK("attack", false),
    SPEED("speed", false),
    // In the attribute calculate these are temporary value need be null.
    HEALTH_PERCENT("health_percent"),
    DEFENCE_PERCENT("defence_percent"),
    ATTACK_PERCENT("attack_percent"),
    SPEED_PERCENT("speed_percent"),
    // end value

    CRIT_CHANCE("crit_chance"),
    CRIT_ATTACK("crit_attack"),
    EFFECT_HIT_RATE("effect_hit_rate"),
    EFFECT_RESISTANCE("effect_resistance"),

    OUTGOING_HEALING_BOOST("outgoing_healing_boost"),
    HEAL_TAKEN_RATIO("heal_taken_ratio"),

    BREAKING_EFFECT("breaking_effect"),
    ENERGY_REGENERATION_RATE("energy_regeneration_rate"),

    PHYSICAL_DAMAGE_BOOST("physical_damage_boost"),
    FIRE_DAMAGE_BOOST("fire_damage_boost"),
    ICE_DAMAGE_BOOST("ice_damage_boost"),
    THUNDER_DAMAGE_BOOST("thunder_damage_boost"),
    WIND_DAMAGE_BOOST("wind_damage_boost"),
    QUANTUM_DAMAGE_BOOST("quantum_damage_boost"),
    IMAGINARY_DAMAGE_BOOST("imaginary_damage_boost"),

    DAMAGE_PENETRATION("damage_penetration"),
    DEFENCE_IGNORE("defence_ignore"),

    ALL_DAMAGE_TYPE_BOOST("all_damage_type_boost"),

    ELATION_DAMAGE_BOOST("elation_damage_boost");

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
