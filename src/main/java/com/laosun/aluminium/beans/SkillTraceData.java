package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.skill.SkillTrace;

import java.util.List;

/**
 * One trace node (行迹点) of a character's trace tree, as it appears in the trace table.
 *
 * <p>Deserialized from {@code point.json}. Each node has a unique id, the ids of its prerequisites, a type
 * string, and an optional attribute bonus. The tree structure is assembled in
 * {@link SkillTrace#init(int)}.
 *
 * <p><b>Naming.</b> This used to be {@code SkillPoint} — the same name as the <b>battle</b> resource
 * (战技点, {@code models.skillpoint.SkillPointPolicy}), which is a different concept entirely. The trace
 * side is now consistently {@code SkillTrace*}.
 *
 * <p>⚠ The JSON keys still say {@code point_*}. They are the file's contract with the generator, so they
 * are renamed together with the file itself (and the generator) rather than one side at a time — renaming
 * only the Java side here keeps the existing data readable instead of silently binding every field to 0
 * the way a misspelled {@code @SerializedName} would.
 */
public record SkillTraceData(@SerializedName("point_id") int traceId,
                             @SerializedName("pre_point") List<Integer> prevTrace,
                             @SerializedName("point_type") String traceType, Attribute attribute) {

    /**
     * The attribute bonus granted by this trace node.
     */
    public record Attribute(AttributeType name, double value) {
    }
}
