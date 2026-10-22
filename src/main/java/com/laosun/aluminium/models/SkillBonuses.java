package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.SkillType;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-battle skill raises: level by slot, level by memosprite data slot, and extra hit segments.
 *
 * <p><b>Why this is separate.</b> A skill instance belongs to a character that a stage can put into
 * more than one battle, so writing a raised level onto it would stack once per battle. These are facts
 * about this combatant in this battle and share the skill-slot key space.
 */
public final class SkillBonuses {

    private final Map<SkillType, Integer> levels = new HashMap<>();
    private final Map<Integer, Integer> memospriteLevels = new HashMap<>();
    private final Map<Integer, Integer> hitBonus = new HashMap<>();

    /**
     * @param slot  the slot
     * @param delta the extra hit segments
     */
    public void raiseHits(int slot, int delta) {
        if (slot > 0 && delta != 0) {
            hitBonus.merge(slot, delta, Integer::sum);
        }
    }

    /**
     * @param slot the slot
     * @return the extra hit segments
     */
    public int hitBonus(int slot) {
        return hitBonus.getOrDefault(slot, 0);
    }

    /**
     * @param slot  the skill slot
     * @param delta the level raise
     */
    public void raiseLevel(SkillType slot, int delta) {
        if (slot != null && delta != 0) {
            levels.merge(slot, delta, Integer::sum);
        }
    }

    /**
     * @param dataSlot the memosprite data slot
     * @param delta    the level raise
     */
    public void raiseMemospriteLevel(int dataSlot, int delta) {
        if (dataSlot > 0 && delta != 0) {
            memospriteLevels.merge(dataSlot, delta, Integer::sum);
        }
    }

    /**
     * @param dataSlot the memosprite data slot
     * @return the level raise
     */
    public int memospriteLevel(int dataSlot) {
        return memospriteLevels.getOrDefault(dataSlot, 0);
    }

    /**
     * @param slot the skill slot
     * @return the level raise
     */
    public int level(SkillType slot) {
        return slot == null ? 0 : levels.getOrDefault(slot, 0);
    }

    /**
     * Clears the level raises a new battle must not inherit.
     */
    public void clearLevels() {
        levels.clear();
        memospriteLevels.clear();
    }
}
