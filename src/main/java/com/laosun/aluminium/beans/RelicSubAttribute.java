package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.AttributeType;

/**
 * Relic sub-attribute value tables deserialized from sub_attribute.json.
 *
 * <p>Organized by star level (2-5). Each {@link AttributeGroup} contains
 * the base and bonus values for all possible sub-attribute types.
 * Final value = {@code base * (promoteLevel + 1) + bonus * attributeLevel}.
 */
public record RelicSubAttribute(
        @SerializedName("2") AttributeGroup star2,
        @SerializedName("3") AttributeGroup star3,
        @SerializedName("4") AttributeGroup star4,
        @SerializedName("5") AttributeGroup star5
) {
    /**
     * Returns the sub-attribute value group for the given star level.
     *
     * @param star relic star level (2-5)
     * @return the attribute group for that star level
     * @throws IllegalStateException if star is outside 2-5
     */
    public AttributeGroup getAttributeGroupByStar(int star) {
        return switch (star) {
            case 2 -> star2;
            case 3 -> star3;
            case 4 -> star4;
            case 5 -> star5;
            default -> throw new IllegalStateException("Unexpected star: " + star);
        };
    }

    /**
     * All possible sub-attribute values for a given star level.
     *
     * <p>Each field holds both a {@link Attribute#base()} and {@link Attribute#bonus()}
     * for computing the final sub-attribute value.
     */
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

        /**
         * Looks up the value entry for a specific attribute type.
         *
         * @param attributeName the attribute to look up
         * @return the corresponding {@code Attribute} value entry
         * @throws IllegalStateException if the attribute is not a valid sub-attribute
         */
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

    /**
     * A sub-attribute value entry with base and per-level bonus components.
     *
     * <p>Final value = {@code base * (promoteLevel + 1) + bonus * attributeLevel}.
     */
    public record Attribute(double base, double bonus) {
    }
}
