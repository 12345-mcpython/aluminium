package com.laosun.aluminium.state;

import com.laosun.aluminium.enums.SkillType;

import java.util.HashMap;
import java.util.List;
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
    /**
     * One 「伤害倍率提高 N%」 clause on a slot's row share, with an optional enemy-count window.
     *
     * <p>Kept HERE (not on the combatant) because it is per-battle bookkeeping about skill slots, exactly like the level
     * raises above - and because {@code CanHitCompositionTest} refuses raw collections on the combatant.
     */
    public record MultiplierAmendment(double percent, Integer minEnemies, Integer maxEnemies) {
        boolean applies(int livingEnemies) {
            return (minEnemies == null || livingEnemies >= minEnemies)
                    && (maxEnemies == null || livingEnemies <= maxEnemies);
        }
    }

    private final Map<SkillType, List<MultiplierAmendment>> multipliers = new HashMap<>();

    /**
     * Files 「<b>that slot's</b> damage multiplier is raised by {@code percent}」 (2.4 = ×3.4).
     *
     * <p>Several clauses may be filed for one slot; at read time the ones whose enemy-count window holds are ADDED
     * (content uses disjoint windows - the tiers of one sentence - which is why a sum is the right reading).
     *
     * @param slot       the skill slot
     * @param percent    the share the row is raised by
     * @param minEnemies lower enemy-count bound, or {@code null}
     * @param maxEnemies upper enemy-count bound, or {@code null}
     */
    public void raiseMultiplier(SkillType slot, double percent, Integer minEnemies, Integer maxEnemies) {
        if (slot == null) {
            return;
        }
        multipliers.computeIfAbsent(slot, key -> new java.util.ArrayList<>())
                .add(new MultiplierAmendment(percent, minEnemies, maxEnemies));
    }

    /**
     * @param slot          the skill slot
     * @param livingEnemies how many enemies are alive right now
     * @return the factor that slot's row share is multiplied by; {@code 1} when nothing applies
     */
    public double multiplier(SkillType slot, int livingEnemies) {
        List<MultiplierAmendment> filed = multipliers.get(slot);
        if (filed == null) {
            return 1;
        }
        double factor = 1;
        for (MultiplierAmendment amendment : filed) {
            if (amendment.applies(livingEnemies)) {
                factor += amendment.percent();
            }
        }
        return factor;
    }

}
