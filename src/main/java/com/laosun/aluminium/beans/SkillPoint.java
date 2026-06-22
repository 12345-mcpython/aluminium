package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.AttributeType;

import java.util.List;

/**
 * A single skill point (trace node) in a character's skill tree.
 *
 * <p>Deserialized from point.json. Each node has a unique ID, a list of
 * prerequisite point IDs, a type string, and an optional attribute bonus.
 * The tree structure is assembled in {@link com.laosun.aluminium.models.SkillPoint#init(int)}.
 */
public record SkillPoint(@SerializedName("point_id") int pointId, @SerializedName("pre_point") List<Integer> prePoint,
                         @SerializedName("point_type") String pointType, Attribute attribute) {

    /**
     * The attribute bonus granted by this skill point.
     */
    public record Attribute(AttributeType name, double value) {
    }
}
