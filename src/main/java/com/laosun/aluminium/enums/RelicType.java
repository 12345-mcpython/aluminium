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
     * The part vocabulary used by {@code relic_sets.json}'s {@code parts[].type}, mapped to the slot the
     * piece occupies.
     *
     * <p>The generator emits the client's own six part names, which do not all match {@link RelicType}'s
     * names: {@code FOOT} is the boots slot, and the two planar-ornament parts are called {@code NECK}
     * (the sphere) and {@code OBJECT} (the rope). The generator establishes that pairing by crossing the
     * part groups with the main-affix tables — group 55 (damage / ATK / HP / DEF mains) is {@code NECK} and
     * group 56 (break / energy / effect-hit mains) is {@code OBJECT} — so it is data, not a guess about
     * which English word sounds right.
     */
    private static final Map<String, RelicType> SET_PART = Map.of(
            "HEAD", HEAD,
            "HAND", HAND,
            "BODY", BODY,
            "FOOT", BOOT,
            "NECK", BALL,
            "OBJECT", LINE);

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

    /**
     * Resolves a relic-set part type (e.g. {@code FOOT}, {@code NECK}) to the slot it occupies.
     *
     * @param partType the part name exactly as {@code relic_sets.json} spells it
     * @return the slot that holds that part
     * @throws IllegalArgumentException when the part name is null or unknown
     */
    public static RelicType fromSetPart(String partType) {
        RelicType type = partType == null ? null : SET_PART.get(partType.trim());
        if (type == null) {
            throw new IllegalArgumentException("Unknown relic set part type: '" + partType
                    + "' (expected one of " + SET_PART.keySet() + ")");
        }
        return type;
    }
}
