package com.laosun.aluminium.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.enemy.Enemy;

/**
 * Effect hit chance and the seeded rolls.
 *
 * <p><b>Why this is separate.</b> The chance is a pure panel calculation; the roll consumes the one
 * seeded source. Keeping the two together is what lets an AI read the expectation without consuming a
 * draw, and keeps every probability in the engine on the same generator.
 */
public final class ChanceService {

    private final Battle battle;

    /**
     * @param battle the battle whose seeded generator is used
     */
    public ChanceService(Battle battle) {
        this.battle = battle;
    }

    /**
     * @param caster            the applier
     * @param target            the victim
     * @param baseChance        the panel chance
     * @param specificResistKey a specific resistance key, or {@code null}
     * @return the chance in {@code [0, 1]}
     */
    public double hitChance(CanHit caster, CanHit target, double baseChance, String specificResistKey) {
        if (caster == null || target == null) {
            return 0;
        }
        double hit = caster.getAttribute(AttributeType.EFFECT_HIT_RATE).get();
        double resist = target.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        double specific = 0;
        if (target instanceof Enemy enemy && specificResistKey != null) {
            specific = enemy.getDebuffResist().getOrDefault(specificResistKey, 0.0);
        }
        return Math.clamp(baseChance * (1 + hit) * (1 - resist) * (1 - specific), 0, 1);
    }

    /**
     * @param caster            the applier
     * @param target            the victim
     * @param baseChance        the panel chance
     * @param specificResistKey a specific resistance key, or {@code null}
     * @return whether the debuff hit
     */
    public boolean rollDebuff(CanHit caster, CanHit target, double baseChance, String specificResistKey) {
        return battle.getRng().nextDouble() < hitChance(caster, target, baseChance, specificResistKey);
    }

    /**
     * Rolls a flat probability.
     *
     * @param chance a fraction of 1; at or above 1 always passes without consuming a draw
     * @return whether the roll passed
     */
    public boolean roll(double chance) {
        if (chance >= 1) {
            return true;
        }
        if (!(chance > 0)) {
            return false;
        }
        return battle.getRng().nextDouble() < chance;
    }
}
