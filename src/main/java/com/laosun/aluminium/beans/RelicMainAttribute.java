package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public record RelicMainAttribute(@SerializedName("2") Map<String, Map<String, Attribute>> star2,
                                 @SerializedName("3") Map<String, Map<String, Attribute>> star3,
                                 @SerializedName("4") Map<String, Map<String, Attribute>> star4,
                                 @SerializedName("5") Map<String, Map<String, Attribute>> star5) {

    public record Attribute(double base, double bonus) {
    }

    public Map<String, Map<String, Attribute>> getAttributeByStar(int star) {
        return switch (star) {
            case 2 -> star2;
            case 3 -> star3;
            case 4 -> star4;
            case 5 -> star5;
            default -> throw new IllegalArgumentException("Invalid star: " + star);
        };
    }
}
