package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

public record RelicSubAttribute(
        @SerializedName("2") AttributeGroup star2,
        @SerializedName("3") AttributeGroup star3,
        @SerializedName("4") AttributeGroup star4,
        @SerializedName("5") AttributeGroup star5
) {
    public record AttributeGroup(@SerializedName("health_percent") Attribute healthPercent,
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
        public Attribute getAttributeByName(String attributeName) {
            return switch (attributeName) {
                case "health" -> health;
                case "attack" -> attack;
                case "defence" -> defence;
                case "health_percent" -> healthPercent;
                case "attack_percent" -> attackPercent;
                case "defence_percent" -> defencePercent;
                case "speed" -> speed;
                case "crit_chance" -> critChance;
                case "crit_attack" -> critAttack;
                case "effect_hit_rate" -> effectHitRate;
                case "effect_resistance" -> effectResistance;
                case "breaking_effect" -> breakingEffect;
                default -> throw new IllegalStateException("Unexpected attribute: " + attributeName);
            };
        }
    }

    public record Attribute(double base, double bonus) {
    }

    public AttributeGroup getAttributeGroupByStar(int star) {
        return switch (star) {
            case 2 -> star2;
            case 3 -> star3;
            case 4 -> star4;
            case 5 -> star5;
            default -> throw new IllegalStateException("Unexpected star: " + star);
        };
    }
}
