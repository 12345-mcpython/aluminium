package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.models.kit.KitRegistry;

import java.util.List;
import java.util.Map;

/**
 * Facade for the 星魂 (eidolon) system.
 *
 * <p>The actual implementations live in the character kits
 * ({@code com.laosun.aluminium.models.kit}), keyed by character ID.
 * This class keeps the legacy entry points for callers that used
 * {@code Eidolons.build(...)} / {@code Eidolons.skillLevelBonuses(...)}.
 */
public final class Eidolons {

    private Eidolons() {
    }

    /**
     * Returns the skill-level bonuses granted by all ranks up to the given level.
     *
     * @param cid   the character ID
     * @param level the eidolon level (0-6)
     * @return map of skill ID (1 = basic, 2 = skill, 3 = ultimate, 4 = talent)
     *         to bonus levels
     */
    public static Map<Integer, Integer> skillLevelBonuses(int cid, int level) {
        Map<Integer, Eidolon> ranks = Constant.EIDOLONS.get(cid);
        return KitRegistry.skillLevelBonuses(cid, level, ranks);
    }

    /**
     * Builds the eidolon passive traces for the given character and level.
     */
    public static List<Trace> build(int cid, int level) {
        Map<Integer, Eidolon> ranks = Constant.EIDOLONS.get(cid);
        return KitRegistry.eidolons(cid, level, ranks);
    }
}
