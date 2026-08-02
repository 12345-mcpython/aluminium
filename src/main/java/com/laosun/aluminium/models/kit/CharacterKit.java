package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

/**
 * A character kit: everything the battle engine needs for one character —
 * its 行迹 (trace passives), 星魂 (eidolon passives), and any 特殊技能行为
 * (skill behaviors that the generic data-driven executor cannot express).
 *
 * <p>Kits are the single extension point for characters. To add a new
 * character, implement this interface and register it in
 * {@link KitRegistry} — no other engine code needs to change.
 *
 * <p>Characters without a dedicated kit (12** and later) fall back to
 * {@link GenericKit}, which interprets the data-driven passives.
 */
public interface CharacterKit {

    /**
     * The character ID this kit serves (1001, 1102, 8005, ...).
     */
    int cid();

    /**
     * Builds the 行迹技能 (A2/A4/A6 trace passives) of the character.
     *
     * @param points the raw skill-tree nodes from point.json (may be null)
     * @return the implemented trace passives (may be empty)
     */
    default List<Trace> traces(Map<Integer, SkillPoint> points) {
        return List.of();
    }

    /**
     * Builds the 星魂 passive for one rank (ranks 3/5 are pure skill-level
     * bonuses and return {@code null}).
     *
     * @param rank the eidolon rank (1-6)
     * @param data the raw rank data from ranks.json
     * @return the implemented trace, or {@code null} if the rank has no
     *         battle behavior
     */
    default Trace eidolon(int rank, Eidolon data) {
        return null;
    }

    /**
     * Returns the special skill behavior for a skill ID, or {@code null} if
     * the skill can be executed by the generic {@code DataSkill} handlers.
     *
     * @param skillId the skill ID (1 = basic, 2 = skill, 3 = ultimate, 4 = talent)
     */
    default SkillBehavior skillBehavior(int skillId) {
        return null;
    }
}
