package com.laosun.aluminium.battle;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.DoubleValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A unit's aggro weight and the weighted draw built from it.
 *
 * <p><b>Why this is separate from {@code Battle}.</b> The weights are a pure function of the unit's
 * data and attributes - no battle state is read - and the table is the same calculation used by the
 * target selector and by judges. Keeping it next to the turn code made a pure function look like
 * battle state.
 */
public final class AggroTable {

    private AggroTable() {
    }

    /**
     * A unit's current aggro value: the stated weight with its
     * {@link AttributeType#AGGRO_ADDED_RATIO} applied.
     *
     * @param entity the unit
     * @return the aggro value (&gt; 0)
     */
    public static double aggroOf(CanHit entity) {
        double base = baseAggroOf(entity);
        DoubleValue added = entity.getAttribute(AttributeType.AGGRO_ADDED_RATIO);
        if (added == null) {
            return base;
        }
        double multiplier = 1 + added.get();
        // A stated change of -100% or worse would take a living unit out of the draw; keep the stated weight
        // instead of silently deleting the target.
        return multiplier <= 0 ? base : base * multiplier;
    }

    /**
     * The stated aggro weight, before any {@link AttributeType#AGGRO_ADDED_RATIO} is applied.
     *
     * @param entity the unit
     * @return the aggro value (&gt; 0)
     */
    private static double baseAggroOf(CanHit entity) {
        if (entity instanceof Character character) {
            if (character.getAggro() > 0) {
                return character.getAggro();
            }
            return character.getPath().getAggro();
        }
        if (entity.getAggro() > 0) {
            return entity.getAggro();
        }
        return 100;                                  // enemies / summons with nothing stated: the regular tier
    }

    /**
     * The aggro table: {@code unit to hit probability}. The probabilities sum to 1.
     *
     * @param candidates the candidate units (the caller filters out dead targets)
     * @return an ordered unit to probability mapping; an empty list returns an empty table
     */
    public static Map<CanHit, Double> table(List<? extends CanHit> candidates) {
        Map<CanHit, Double> table = new LinkedHashMap<>();
        double total = 0;
        for (CanHit candidate : candidates) {
            total += aggroOf(candidate);
        }
        if (total <= 0) {
            return table;
        }
        for (CanHit candidate : candidates) {
            table.put(candidate, aggroOf(candidate) / total);
        }
        return table;
    }
}
