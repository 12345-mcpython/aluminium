package com.laosun.aluminium.enums;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.Map;

@Getter
public enum RelicType {

    @SerializedName("head") HEAD("head"),
    @SerializedName("body") BODY("body"),
    @SerializedName("hand") HAND("hand"),
    @SerializedName("boot") BOOT("boot"),
    @SerializedName("ball") BALL("ball"),
    @SerializedName("line") LINE("line");

    private static final Map<String, RelicType> MP = Map.ofEntries(Map.entry("head", HEAD), Map.entry("boot", BOOT),
            Map.entry("hand", HAND), Map.entry("body", BODY),
            Map.entry("ball", BALL), Map.entry("line", LINE));
    private final String type;

    RelicType(String string) {
        this.type = string;
    }

    public static RelicType getType(String type) {
        return MP.get(type);
    }
}