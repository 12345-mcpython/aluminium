package com.laosun.aluminium.models.state;

import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.entity.CanHit;

/**
 * The shield currently standing on a combatant, where it came from, and how much the last hit
 * absorbed.
 *
 * <p><b>Why this is separate.</b> A shield overwrites rather than stacks, so the value alone cannot
 * say whether the buff expiring later is allowed to take it off - the installer has to be remembered.
 * Origin (provider / rule id) answers the "shield from this rule" conditions. One object owns all four
 * facts and the absorption arithmetic.
 */
public final class ShieldState {

    private double value;
    private AbstractBuff installer;
    private CanHit provider;
    private String ruleId = "";
    private double lastAbsorbed;

    /**
     * @return the current shield value
     */
    public double value() {
        return value;
    }

    /**
     * Raw write, clearing ownership.
     *
     * @param value the shield value
     */
    public void set(double value) {
        this.value = value;
        installer = null;
        provider = null;
        ruleId = "";
    }

    /**
     * Raw grant with an origin.
     *
     * @param value    the shield value
     * @param provider who granted it
     * @param ruleId   the granting rule id
     */
    public void set(double value, CanHit provider, String ruleId) {
        this.value = value;
        installer = null;
        this.provider = provider;
        this.ruleId = ruleId == null ? "" : ruleId;
    }

    /**
     * Timed installation, remembering the buff that must remove it.
     *
     * @param value     the shield value
     * @param installer the installing buff
     */
    public void install(double value, AbstractBuff installer) {
        this.value = value;
        this.installer = installer;
        provider = installer == null ? null : installer.getSource();
        ruleId = installer == null || installer.getRuleId() == null ? "" : installer.getRuleId();
    }

    /**
     * @return the shield's provider, or {@code null}
     */
    public CanHit provider() {
        return provider;
    }

    /**
     * @return the granting rule id, or {@code ""}
     */
    public String ruleId() {
        return ruleId;
    }

    /**
     * @param installer the expiring buff
     * @return whether it owned the shield and removed it
     */
    public boolean removeFrom(AbstractBuff installer) {
        if (this.installer != installer) {
            return false;
        }
        value = 0;
        this.installer = null;
        provider = null;
        ruleId = "";
        return true;
    }

    /**
     * Drains the shield against one hit.
     *
     * @param damage the incoming damage
     * @return the damage left after absorption
     */
    public double absorb(double damage) {
        lastAbsorbed = 0;
        if (value <= 0) {
            return damage;
        }
        double absorbed = Math.min(value, damage);
        value -= absorbed;
        lastAbsorbed = absorbed;
        return damage - absorbed;
    }

    /**
     * @return how much the last {@link #absorb(double)} blocked
     */
    public double lastAbsorbed() {
        return lastAbsorbed;
    }
}
