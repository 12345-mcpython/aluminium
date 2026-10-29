package com.laosun.aluminium.state;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-combatant firing limits: cooldowns and per-turn uses, keyed by rule.
 *
 * <p><b>Why this is separate.</b> These counters are battle state, not rule state: a table is compiled
 * once and shared by every wearer, so a cooldown written onto the rule would leak across battles. Both
 * maps are cleared at the same moment (the start of this combatant's turn), which is what makes them
 * one component.
 */
public final class TriggerCooldowns {

    private final Map<String, Integer> cooldowns = new HashMap<>();
    private final Map<String, Integer> turnUses = new HashMap<>();

    /**
     * @param key       the rule key
     * @param perTurn   the per-turn cap ({@code 0} = none)
     * @param spentOnce whether the rule has spent a once-per-battle limit
     * @return whether the rule may fire now
     */
    public boolean isReady(String key, int perTurn, boolean spentOnce) {
        if (spentOnce || cooldowns.getOrDefault(key, 0) > 0) {
            return false;
        }
        return perTurn <= 0 || turnUses.getOrDefault(key, 0) < perTurn;
    }

    /**
     * Records one firing.
     *
     * @param key           the rule key
     * @param cooldownTurns the cooldown to start ({@code 0} = none)
     * @param perTurn       the per-turn cap ({@code 0} = none)
     * @param oncePerBattle whether this firing spends a once-per-battle limit
     */
    public void record(String key, int cooldownTurns, int perTurn, boolean oncePerBattle) {
        if (perTurn > 0) {
            turnUses.merge(key, 1, Integer::sum);
        }
        if (oncePerBattle) {
            return;
        }
        if (cooldownTurns > 0) {
            cooldowns.put(key, cooldownTurns);
        }
    }

    /**
     * @param key the rule key
     */
    public void reset(String key) {
        turnUses.remove(key);
        cooldowns.remove(key);
    }

    /**
     * Counts one of the owner's turns off every cooldown and clears the per-turn counters.
     */
    public void tick() {
        turnUses.clear();
        if (cooldowns.isEmpty()) {
            return;
        }
        cooldowns.replaceAll((key, turns) -> turns - 1);
        cooldowns.values().removeIf(turns -> turns <= 0);
    }

    /**
     * Clears every cooldown and per-turn use.
     */
    public void clear() {
        cooldowns.clear();
        turnUses.clear();
    }
}
