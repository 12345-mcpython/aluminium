package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

public record RelicMainAttribute(int star, Part part) {
    public record Part(Ball ball, Head head, Line line, Boot boot, Body body, Hand hand) {
    }

    public record Head(
            @SerializedName("health") Attribute health
    ) {
    }

    public record Ball(
            @SerializedName("health_percent") Attribute healthPercent,
            @SerializedName("defence_percent") Attribute defencePercent,
            @SerializedName("quantum_damage_boost") Attribute quantumDamageBoost,
            @SerializedName("attack_percent") Attribute attackPercent,
            @SerializedName("ice_damage_boost") Attribute iceDamageBoost,
            @SerializedName("thunder_damage_boost") Attribute thunderDamageBoost,
            @SerializedName("fire_damage_boost") Attribute fireDamageBoost,
            @SerializedName("imaginary_damage_boost") Attribute imaginaryDamageBoost,
            @SerializedName("physical_damage_boost") Attribute physicalDamageBoost,
            @SerializedName("wind_damage_boost") Attribute windDamageBoost
    ) {
    }

    public record Line(
            @SerializedName("health_percent") Attribute healthPercent,
            @SerializedName("defence_percent") Attribute defencePercent,
            @SerializedName("attack_percent") Attribute attackPercent,
            @SerializedName("energy_regeneration_rate") Attribute energyRegenerationRate,
            @SerializedName("breaking_effect") Attribute breakingEffect
    ) {
    }

    public record Boot(
            @SerializedName("health_percent") Attribute healthPercent,
            @SerializedName("defence_percent") Attribute defencePercent,
            @SerializedName("attack_percent") Attribute attackPercent,
            @SerializedName("speed") Attribute speed
    ) {
    }

    public record Body(
            @SerializedName("health_percent") Attribute healthPercent,
            @SerializedName("defence_percent") Attribute defencePercent,
            @SerializedName("crit_attack") Attribute critAttack,
            @SerializedName("attack_percent") Attribute attackPercent,
            @SerializedName("effect_hit_rate") Attribute effectHitRate,
            @SerializedName("outgoing_healing_boost") Attribute outgoingHealingBoost,
            @SerializedName("crit_chance") Attribute critChance
    ) {
    }

    public record Hand(
            @SerializedName("attack") Attribute attack
    ) {
    }

    public record Attribute(double base, double bonus) {
    }
}
