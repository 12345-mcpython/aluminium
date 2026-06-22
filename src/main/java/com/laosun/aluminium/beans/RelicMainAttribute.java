package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;

import java.util.Map;

/**
 * Relic main attribute value tables deserialized from main_attribute.json.
 *
 * <p>Organized by star level (2-5), then by relic type, then by attribute type.
 * Each attribute entry has a {@link Attribute#base()} and {@link Attribute#bonus()}
 * value used to compute the final stat at a given relic level.
 */
public record RelicMainAttribute(@SerializedName("2") Map<RelicType, Map<AttributeType, Attribute>> star2,
                                 @SerializedName("3") Map<RelicType, Map<AttributeType, Attribute>> star3,
                                 @SerializedName("4") Map<RelicType, Map<AttributeType, Attribute>> star4,
                                 @SerializedName("5") Map<RelicType, Map<AttributeType, Attribute>> star5) {

    /**
     * Returns the main attribute table for the given star level.
     *
     * @param star relic star level (2-5)
     * @return a map from relic type to attribute-to-value mapping
     * @throws IllegalArgumentException if star is outside 2-5
     */
    public Map<RelicType, Map<AttributeType, Attribute>> getAttributeByStar(int star) {
        return switch (star) {
            case 2 -> star2;
            case 3 -> star3;
            case 4 -> star4;
            case 5 -> star5;
            default -> throw new IllegalArgumentException("Invalid star: " + star);
        };
    }

    /**
     * A main attribute value entry with base and per-level bonus components.
     *
     * <p>Final value = {@code base + bonus * relicLevel}.
     */
    public record Attribute(double base, double bonus) {
    }
}
