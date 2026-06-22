package com.laosun.aluminium.enums;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.Map;

/**
 * The six equipment slots on a character that can hold relics.
 *
 * <p>Each type has a fixed pool of possible main attributes and can
 * appear exactly once in a {@link com.laosun.aluminium.models.RelicSuit}.
 */
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

    /**
     * The string identifier used in JSON serialization.
     */
    private final String type;

    RelicType(String string) {
        this.type = string;
    }

    /**
     * Looks up a relic type by its string identifier.
     *
     * @param type the type string, e.g. "head", "body"
     * @return the matching {@code RelicType}, or {@code null} if not found
     */
    public static RelicType getType(String type) {
        return MP.get(type);
    }
}
