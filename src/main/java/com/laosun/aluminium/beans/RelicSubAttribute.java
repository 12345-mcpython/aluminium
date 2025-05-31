package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

public record RelicSubAttribute(int star, Attributes attributes) {
    public record Attributes(@SerializedName("health_percent") Attribute healthPercent,
                             @SerializedName("defence_percent") Attribute defencePercent,
                             @SerializedName("crit_attack") Attribute critAttack,
                             @SerializedName("defence") Attribute defence,
                             @SerializedName("attack") Attribute attack,
                             @SerializedName("attack_percent") Attribute attackPercent,
                             @SerializedName("effect_hit_rate") Attribute effectHitRate,
                             @SerializedName("effect_resistance") Attribute effectResistance,
                             @SerializedName("health") Attribute health,
                             @SerializedName("breaking_effect") Attribute breakingEffect,
                             @SerializedName("speed") Attribute speed,
                             @SerializedName("crit_chance") Attribute critChance) {
    }

    public record Attribute(double base, double bonus) {
    }
}
