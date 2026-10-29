package com.laosun.aluminium.models;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-battle chance state: a rule's decayed probability and the last pick under a no-repeat memory.
 *
 * <p><b>Why this is separate.</b> Both are battle facts keyed by a name the data states, and both are
 * cleared together at a new battle. Keeping them on the combatant next to the firing limits made the
 * "current probability" look like a property of the rule.
 */
public final class RuleChanceState {

    private final Map<String, Double> currentChances = new HashMap<>();
    private final Map<String, Integer> pickMemory = new HashMap<>();

    /**
     * @param memory the memory name
     * @return the last picked effect index, or {@code -1}
     */
    public int lastPick(String memory) {
        if (memory == null || memory.isBlank()) {
            return -1;
        }
        return pickMemory.getOrDefault(memory.trim(), -1);
    }

    /**
     * @param memory      the memory name
     * @param effectIndex the effect just picked
     */
    public void rememberPick(String memory, int effectIndex) {
        if (memory != null && !memory.isBlank()) {
            pickMemory.put(memory.trim(), effectIndex);
        }
    }

    /**
     * @param ruleId  the rule id
     * @param initial the rule's initial chance
     * @return the current chance
     */
    public double currentChance(String ruleId, double initial) {
        return ruleId == null || ruleId.isBlank() ? initial
                : currentChances.getOrDefault(ruleId.trim(), initial);
    }

    /**
     * @param ruleId  the rule id
     * @param initial the rule's initial chance
     * @param decay   the decay factor
     */
    public void decay(String ruleId, double initial, double decay) {
        if (ruleId == null || ruleId.isBlank() || !(decay > 0)) {
            return;
        }
        currentChances.put(ruleId.trim(), currentChance(ruleId, initial) * decay);
    }

    /**
     * @param ruleId the rule id
     */
    public void resetChance(String ruleId) {
        if (ruleId != null && !ruleId.isBlank()) {
            currentChances.remove(ruleId.trim());
        }
    }

    /**
     * Clears every chance and pick memory.
     */
    public void clear() {
        currentChances.clear();
        pickMemory.clear();
    }
}
