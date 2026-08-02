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
 *
 * <p>行迹技能 (trace skill) nodes ({@code point_type == "skill"}) additionally carry
 * their translated name/description, the {@code effect} IDs and {@code param}
 * values so the battle engine can implement the passive abilities.
 */
public record SkillPoint(@SerializedName("point_id") int pointId, @SerializedName("pre_point") List<Integer> prePoint,
                         @SerializedName("point_type") String pointType, Attribute attribute,
                         Translate name, Translate desc,
                         @SerializedName("effect") List<Integer> effect,
                         @SerializedName("param") List<Double> param) {

    /**
     * The attribute bonus granted by this skill point.
     */
    public record Attribute(AttributeType name, double value) {
    }
}
