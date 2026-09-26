package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.skill.SkillTrace;

import java.util.List;

/**
 * One trace node (行迹点) of a character's trace tree, as it appears in the trace table.
 *
 * <p>Deserialized from {@code skill_traces.json}. Each node has a unique id, the ids of its
 * prerequisites, a type string, and an optional attribute bonus. The tree structure is assembled in
 * {@link SkillTrace#init(int)}.
 *
 * <p><b>Naming.</b> This used to be {@code SkillPoint} — the same name as the <b>battle</b> resource
 * (战技点, {@code models.skillpoint.SkillPointPolicy}), which is a different concept entirely. The trace
 * side is now consistently {@code SkillTrace*} on all three sides: this bean, {@code Constant.SKILL_TRACES},
 * and the generator's {@code trace_id} / {@code prev_trace} / {@code trace_type} export. The JSON keys had
 * to move <b>with</b> the data: {@code @SerializedName} is that contract, so renaming them ahead of the file
 * would have silently bound every component to 0.
 */
public record SkillTraceData(@SerializedName("trace_id") int traceId,
                             @SerializedName("prev_trace") List<Integer> prevTrace,
                             @SerializedName("trace_type") String traceType, Attribute attribute) {

    /**
     * The attribute bonus granted by this trace node.
     */
    public record Attribute(AttributeType name, double value) {
    }
}
