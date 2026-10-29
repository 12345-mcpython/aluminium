package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.damage.BattleDamage;
import com.laosun.aluminium.models.entity.CanHit;

/**
 * Healing and shield application.
 *
 * <p><b>Why this is separate.</b> Both are "compute the amount, apply it, announce the amount that
 * actually landed" - and the announcement is the part every caller would otherwise re-derive. The
 * arithmetic and the notification are one subject; the turn loop has no say in either.
 */
public final class SustainService {

    /**
     * Announces a heal that actually restored HP.
     */
    @FunctionalInterface
    public interface HealNotifier {
        /**
         * @param healer who healed
         * @param target who was healed
         * @param healed how much was restored
         */
        void settled(CanHit healer, CanHit target, double healed);
    }

    /**
     * Announces a shield that was actually granted.
     */
    @FunctionalInterface
    public interface ShieldNotifier {
        /**
         * @param provider who granted it
         * @param target   who received it
         * @param value    the value actually set
         */
        void granted(CanHit provider, CanHit target, double value);
    }

    private final Battle battle;
    private final HealNotifier onHeal;
    private final ShieldNotifier onShield;

    /**
     * @param battle   the battle whose heal calculation is used
     * @param onHeal   announces a landed heal
     * @param onShield announces a granted shield
     */
    public SustainService(Battle battle, HealNotifier onHeal, ShieldNotifier onShield) {
        this.battle = battle;
        this.onHeal = onHeal;
        this.onShield = onShield;
    }

    /**
     * Performs one heal.
     *
     * @param healer     who heals
     * @param target     who is healed
     * @param baseAmount the base amount
     * @return the HP actually restored
     */
    public double heal(CanHit healer, CanHit target, double baseAmount) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        double amount = battle.calculateHeal(healer, target, baseAmount);
        if (amount <= 0) {
            return 0;
        }
        double before = target.getCurrentHp();
        target.heal(amount);
        double healed = target.getCurrentHp() - before;
        if (healed > 0) {
            onHeal.settled(healer, target, healed);
        }
        return healed;
    }

    /**
     * Grants a shield, boosted by {@code provider}'s own shield boost.
     *
     * @param provider who grants it ({@code null} = unknown, unboosted)
     * @param target   who receives it
     * @param amount   the amount before the boost
     * @param ruleId   the rule granting it ({@code ""} = unnamed)
     * @return the shield value actually set
     */
    public double grantShield(CanHit provider, CanHit target, double amount, String ruleId) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        double value = BattleDamage.boostedShield(provider, amount);
        target.setShield(value, provider, ruleId);
        if (value > 0) {
            onShield.granted(provider, target, value);
        }
        return value;
    }
}
