package com.laosun.aluminium.state;

import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.Skill;

import java.util.EnumMap;
import java.util.Map;

/**
 * The six skill slots of a combatant, keyed by {@link SkillType}.
 *
 * <p><b>Why this is separate.</b> The map is configuration, not battle progress: it is copied when a
 * character enters a battle and then read by every skill/energy/trigger path. One component keeps the
 * "slot → skill" contract in one place instead of a bare map on the combatant.
 */
public final class SkillSet {

    private final EnumMap<SkillType, Skill> skills = new EnumMap<>(SkillType.class);

    /**
     * @param slot  the slot
     * @param skill the skill
     */
    public void set(SkillType slot, Skill skill) {
        skills.put(slot, skill);
    }

    /**
     * @return the live map, in slot order
     */
    public Map<SkillType, Skill> all() {
        return skills;
    }

    /**
     * @param other the source map
     */
    public void copyFrom(Map<SkillType, Skill> other) {
        skills.clear();
        skills.putAll(other);
    }
}
