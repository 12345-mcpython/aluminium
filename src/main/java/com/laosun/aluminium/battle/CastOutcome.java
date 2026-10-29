package com.laosun.aluminium.battle;

import java.util.HashMap;
import java.util.Map;

/**
 * What the cast being resolved has produced: the states it landed and the laugh count it claimed.
 *
 * <p><b>Why this is a separate object.</b> The counts are not part of the battle's permanent state -
 * they exist for one cast window, are cleared at its beginning and after its events have been
 * delivered, and reading them outside that window must answer zero. Keeping them next to the cast
 * token made "the cast is open" and "what the cast has done" look like one fact; this object owns
 * the latter.
 */
public final class CastOutcome {

    private final Map<String, Integer> applied = new HashMap<>();
    private Integer countedLaughs;
    private Integer nextCountedLaughs;

    /**
     * Records one landed application of a named state during the current cast.
     *
     * @param stateName the state's name
     */
    public void recordApplied(String stateName) {
        if (stateName == null || stateName.isBlank()) {
            return;
        }
        applied.merge(stateName, 1, Integer::sum);
    }

    /**
     * @param stateName the state's name
     * @return how many targets the current cast applied it to
     */
    public int appliedCount(String stateName) {
        return stateName == null ? 0 : applied.getOrDefault(stateName, 0);
    }

    /**
     * Opens a new cast window, consuming any count the previous command claimed.
     */
    public void begin() {
        applied.clear();
        countedLaughs = nextCountedLaughs;
        nextCountedLaughs = null;
    }

    /**
     * Closes the cast window, so nothing firing later reads a stale count.
     */
    public void end() {
        applied.clear();
        countedLaughs = null;
    }

    /**
     * Claims the laugh count for the next cast to begin.
     *
     * @param laughs the count
     */
    public void markNextCountedLaughs(int laughs) {
        nextCountedLaughs = laughs;
    }

    /**
     * @return the laugh count the current cast is settled with, or {@code null}
     */
    public Integer countedLaughs() {
        return countedLaughs;
    }
}
