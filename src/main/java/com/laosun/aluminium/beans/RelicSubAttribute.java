package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.AttributeType;

public record RelicSubAttribute(
        @SerializedName("2") AttributeGroup star2,
        @SerializedName("3") AttributeGroup star3,
        @SerializedName("4") AttributeGroup star4,
        @SerializedName("5") AttributeGroup star5
) {
    public AttributeGroup getAttributeGroupByStar(int star) {
        return switch (star) {
            case 2 -> star2;
            case 3 -> star3;
            case 4 -> star4;
            case 5 -> star5;
            default -> throw new IllegalStateException("Unexpected star: " + star);
        };
    }

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
        public Attribute getAttributeByName(AttributeType attributeName) {
            return switch (attributeName) {
                case HEALTH -> health;
                case ATTACK -> attack;
                case DEFENCE -> defence;
                case HEALTH_PERCENT -> healthPercent;
                case ATTACK_PERCENT -> attackPercent;
                case DEFENCE_PERCENT -> defencePercent;
                case SPEED -> speed;
                case CRIT_CHANCE -> critChance;
                case CRIT_ATTACK -> critAttack;
                case EFFECT_HIT_RATE -> effectHitRate;
                case EFFECT_RESISTANCE -> effectResistance;
                case BREAKING_EFFECT -> breakingEffect;
                default -> throw new IllegalStateException("Unexpected attribute: " + attributeName);
            };
        }
    }

    public record Attribute(double base, double bonus) {
    }
}
