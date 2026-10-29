package com.laosun.aluminium.models.state;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-battle amendments to another rule's own numbers, keyed by that rule's id.
 *
 * <p><b>Why this is separate.</b> A trigger table is compiled once and shared by every wearer, so a
 * raised {@code per_turn} written onto the rule would leak across battles. These are facts about this
 * combatant in this battle ("I have Eidolon 4 active") and are cleared with the other rule bookkeeping.
 */
public final class RuleAmendments {

    private final Map<String, Integer> perTurn = new HashMap<>();
    private final Map<String, Double> baseChance = new HashMap<>();
    private final Map<String, Double> effectPercent = new HashMap<>();
    private final Map<String, Integer> effectTurns = new HashMap<>();
    private final Map<String, Integer> cooldownDelta = new HashMap<>();
    private final Map<String, Integer> effectMaxStacks = new HashMap<>();

    /**
     * @param ruleId the rule id
     * @param delta  the extra firings per turn
     */
    public void addPerTurn(String ruleId, int delta) {
        if (ruleId != null && !ruleId.isBlank()) {
            perTurn.merge(ruleId.trim(), delta, Integer::sum);
        }
    }

    /**
     * @param ruleId the rule id
     * @param delta  the extra base chance
     */
    public void addBaseChance(String ruleId, double delta) {
        if (ruleId != null && !ruleId.isBlank()) {
            baseChance.merge(ruleId.trim(), delta, Double::sum);
        }
    }

    /**
     * @param ruleId the rule id
     * @param delta  the effect-percent amendment
     */
    public void addEffectPercent(String ruleId, double delta) {
        if (ruleId != null && !ruleId.isBlank()) {
            effectPercent.merge(ruleId.trim(), delta, Double::sum);
        }
    }

    /**
     * @param ruleId the rule id
     * @param delta  the cooldown amendment
     */
    public void addCooldownDelta(String ruleId, int delta) {
        if (ruleId != null && !ruleId.isBlank()) {
            cooldownDelta.merge(ruleId.trim(), delta, Integer::sum);
        }
    }

    /**
     * @param ruleId the rule id
     * @param delta  the effect-turns amendment
     */
    public void addEffectTurns(String ruleId, int delta) {
        if (ruleId != null && !ruleId.isBlank()) {
            effectTurns.merge(ruleId.trim(), delta, Integer::sum);
        }
    }

    /**
     * @param ruleId the rule id
     * @param delta  the max-stacks amendment
     */
    public void addEffectMaxStacks(String ruleId, int delta) {
        if (ruleId != null && !ruleId.isBlank()) {
            effectMaxStacks.merge(ruleId.trim(), delta, Integer::sum);
        }
    }

    /**
     * @param ruleId the rule id
     * @return the per-turn amendment
     */
    public int perTurn(String ruleId) {
        return ruleId == null || ruleId.isBlank() ? 0 : perTurn.getOrDefault(ruleId.trim(), 0);
    }

    /**
     * @param ruleId the rule id
     * @return the base-chance amendment
     */
    public double baseChance(String ruleId) {
        return ruleId == null || ruleId.isBlank() ? 0 : baseChance.getOrDefault(ruleId.trim(), 0.0);
    }

    /**
     * @param ruleId the rule id
     * @return the cooldown amendment
     */
    public int cooldownDelta(String ruleId) {
        return ruleId == null || ruleId.isBlank() ? 0 : cooldownDelta.getOrDefault(ruleId.trim(), 0);
    }

    /**
     * @param ruleId the rule id
     * @return the effect-percent amendment, or {@code null}
     */
    public Double effectPercent(String ruleId) {
        return ruleId == null || ruleId.isBlank() ? null : effectPercent.get(ruleId.trim());
    }

    /**
     * @param ruleId the rule id
     * @return the effect-turns amendment, or {@code null}
     */
    public Integer effectTurns(String ruleId) {
        return ruleId == null || ruleId.isBlank() ? null : effectTurns.get(ruleId.trim());
    }

    /**
     * @param ruleId the rule id
     * @return the max-stacks amendment, or {@code null}
     */
    public Integer effectMaxStacks(String ruleId) {
        return ruleId == null || ruleId.isBlank() ? null : effectMaxStacks.get(ruleId.trim());
    }

    /**
     * Clears the amendments a new battle must not inherit.
     */
    public void clearForNewBattle() {
        perTurn.clear();
        baseChance.clear();
        effectPercent.clear();
        effectTurns.clear();
        effectMaxStacks.clear();
    }
}
