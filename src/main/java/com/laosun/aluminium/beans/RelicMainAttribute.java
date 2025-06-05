package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

public record RelicMainAttribute(@SerializedName("2") Part star2,
                                 @SerializedName("3") Part star3,
                                 @SerializedName("4") Part star4,
                                 @SerializedName("5") Part star5) {
    public record Part(BallAttributeGroup ball, HeadAttributeGroup head, LineAttributeGroup line,
                       BootAttributeGroup boot, BodyAttributeGroup body, HandAttributeGroup hand) {
        public Record getPartByName(String name) {
            return switch (name) {
                case "ball" -> ball;
                case "head" -> head;
                case "line" -> line;
                case "boot" -> boot;
                case "body" -> body;
                case "hand" -> hand;
                default -> throw new IllegalStateException("Unexpected part: " + name);
            };
        }
    }

    public record HeadAttributeGroup(
            @SerializedName("health") Attribute health
    ) {
    }

    public record BallAttributeGroup(
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
        public Attribute getAttributeByName(String name) {
            return switch (name) {
                case "health_percent" -> healthPercent;
                case "defence_percent" -> defencePercent;
                case "quantum_damage_boost" -> quantumDamageBoost;
                case "attack_percent" -> attackPercent;
                case "ice_damage_boost" -> iceDamageBoost;
                case "thunder_damage_boost" -> thunderDamageBoost;
                case "fire_damage_boost" -> fireDamageBoost;
                case "physical_damage_boost" -> physicalDamageBoost;
                case "wind_damage_boost" -> windDamageBoost;
                case "imaginary_damage_boost" -> imaginaryDamageBoost;
                default -> throw new IllegalStateException("Unexpected attribute: " + name);
            };
        }
    }

    public record LineAttributeGroup(
            @SerializedName("health_percent") Attribute healthPercent,
            @SerializedName("defence_percent") Attribute defencePercent,
            @SerializedName("attack_percent") Attribute attackPercent,
            @SerializedName("energy_regeneration_rate") Attribute energyRegenerationRate,
            @SerializedName("breaking_effect") Attribute breakingEffect
    ) {
        public Attribute getAttributeByName(String name) {
            return switch (name) {
                case "health_percent" -> healthPercent;
                case "defence_percent" -> defencePercent;
                case "attack_percent" -> attackPercent;
                case "energy_regeneration_rate" -> energyRegenerationRate;
                case "breaking_effect" -> breakingEffect;
                default -> throw new IllegalStateException("Unexpected attribute: " + name);
            };
        }
    }

    public record BootAttributeGroup(
            @SerializedName("health_percent") Attribute healthPercent,
            @SerializedName("defence_percent") Attribute defencePercent,
            @SerializedName("attack_percent") Attribute attackPercent,
            @SerializedName("speed") Attribute speed
    ) {
        public Attribute getAttributeByName(String name) {
            return switch (name) {
                case "health_percent" -> healthPercent;
                case "defence_percent" -> defencePercent;
                case "attack_percent" -> attackPercent;
                case "speed" -> speed;
                default -> throw new IllegalStateException("Unexpected attribute: " + name);
            };
        }
    }

    public record BodyAttributeGroup(
            @SerializedName("health_percent") Attribute healthPercent,
            @SerializedName("defence_percent") Attribute defencePercent,
            @SerializedName("crit_attack") Attribute critAttack,
            @SerializedName("attack_percent") Attribute attackPercent,
            @SerializedName("effect_hit_rate") Attribute effectHitRate,
            @SerializedName("outgoing_healing_boost") Attribute outgoingHealingBoost,
            @SerializedName("crit_chance") Attribute critChance
    ) {
        public Attribute getAttributeByName(String name) {
            return switch (name) {
                case "health_percent" -> healthPercent;
                case "defence_percent" -> defencePercent;
                case "attack_percent" -> attackPercent;
                case "crit_attack" -> critAttack;
                case "effect_hit_rate" -> effectHitRate;
                case "outgoing_healing_boost" -> outgoingHealingBoost;
                case "crit_chance" -> critChance;
                default -> throw new IllegalStateException("Unexpected attribute: " + name);
            };
        }
    }

    public record HandAttributeGroup(
            @SerializedName("attack") Attribute attack
    ) {
    }

    public record Attribute(double base, double bonus) {
    }

    public Part getPartByStar(int star) {
        return switch (star) {
            case 2 -> star2;
            case 3 -> star3;
            case 4 -> star4;
            case 5 -> star5;
            default -> throw new IllegalStateException("Unexpected star: " + star);
        };
    }
}
