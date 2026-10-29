package com.laosun.aluminium.state;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-combatant rule limits that are not cooldowns: once per attack, once per wave, and once per
 * battle.
 *
 * <p><b>Why this is separate.</b> All three answer "has this rule already had its allowance for the
 * current sequence?" and all three are battle state keyed by the rule, not properties of the compiled
 * rule (which is shared by every wearer).
 */
public final class RuleLimits {

    private final Map<String, Integer> attackSequences = new HashMap<>();
    private final Map<String, Integer> attackFirings = new HashMap<>();
    private final Map<String, Integer> waveSequences = new HashMap<>();
    private final Set<String> spentOnce = new HashSet<>();

    /**
     * @param key           the rule key
     * @param attackSequence the current attack sequence
     * @param cap           how many times the rule may fire in that attack
     * @return whether it may fire now
     */
    public boolean isAttackReady(String key, int attackSequence, int cap) {
        Integer sequence = attackSequences.get(key);
        if (sequence == null || sequence.intValue() != attackSequence) {
            return true;
        }
        return attackFirings.getOrDefault(key, 0) < cap;
    }

    /**
     * @param key            the rule key
     * @param attackSequence the current attack sequence
     */
    public void recordAttack(String key, int attackSequence) {
        Integer sequence = attackSequences.get(key);
        if (sequence == null || sequence.intValue() != attackSequence) {
            attackSequences.put(key, attackSequence);
            attackFirings.put(key, 1);
        } else {
            attackFirings.merge(key, 1, Integer::sum);
        }
    }

    /**
     * @param key          the rule key
     * @param waveSequence the current wave sequence
     * @return whether it may fire now
     */
    public boolean isWaveReady(String key, int waveSequence) {
        Integer sequence = waveSequences.get(key);
        return sequence == null || sequence.intValue() != waveSequence;
    }

    /**
     * @param key          the rule key
     * @param waveSequence the current wave sequence
     */
    public void recordWave(String key, int waveSequence) {
        waveSequences.put(key, waveSequence);
    }

    /**
     * @param key the rule key
     * @return whether the rule has spent its once-per-battle allowance
     */
    public boolean hasSpentOnce(String key) {
        return spentOnce.contains(key);
    }

    /**
     * @param key the rule key
     */
    public void spendOnce(String key) {
        spentOnce.add(key);
    }

    /**
     * @param key the rule key
     */
    public void reset(String key) {
        attackSequences.remove(key);
        attackFirings.remove(key);
        waveSequences.remove(key);
        spentOnce.remove(key);
    }

    /**
     * Clears every limit.
     */
    public void clear() {
        attackSequences.clear();
        attackFirings.clear();
        waveSequences.clear();
        spentOnce.clear();
    }
}
