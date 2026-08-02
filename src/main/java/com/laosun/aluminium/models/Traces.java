package com.laosun.aluminium.models;

import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.models.kit.KitRegistry;

import java.util.List;
import java.util.Map;

/**
 * Facade for building the 行迹技能 (trace passives) of a character.
 *
 * <p>The actual implementations live in the character kits
 * ({@code com.laosun.aluminium.models.kit}), keyed by character ID.
 * This class keeps the legacy entry point for callers that used
 * {@code Traces.build(...)}.
 */
public final class Traces {

    private Traces() {
    }

    /**
     * Builds the trace passives for the given character.
     *
     * @param cid    the character ID
     * @param points the raw skill-tree nodes (or null if unavailable)
     * @return the list of implemented trace passives (may be empty)
     */
    public static List<Trace> build(int cid, List<SkillPoint> points) {
        Map<Integer, SkillPoint> byId = new java.util.HashMap<>();
        if (points != null) {
            for (SkillPoint point : points) {
                if ("skill".equals(point.pointType())) {
                    byId.put(point.pointId(), point);
                }
            }
        }
        return KitRegistry.traces(cid, byId);
    }
}
