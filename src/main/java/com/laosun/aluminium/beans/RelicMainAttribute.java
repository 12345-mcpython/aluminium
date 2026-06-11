package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;

import java.util.Map;

public record RelicMainAttribute(@SerializedName("2") Map<RelicType, Map<AttributeType, Attribute>> star2,
                                 @SerializedName("3") Map<RelicType, Map<AttributeType, Attribute>> star3,
                                 @SerializedName("4") Map<RelicType, Map<AttributeType, Attribute>> star4,
                                 @SerializedName("5") Map<RelicType, Map<AttributeType, Attribute>> star5) {

    public Map<RelicType, Map<AttributeType, Attribute>> getAttributeByStar(int star) {
        return switch (star) {
            case 2 -> star2;
            case 3 -> star3;
            case 4 -> star4;
            case 5 -> star5;
            default -> throw new IllegalArgumentException("Invalid star: " + star);
        };
    }

    public record Attribute(double base, double bonus) {
    }
}
