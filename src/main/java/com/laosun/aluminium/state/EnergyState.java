package com.laosun.aluminium.state;

import com.laosun.aluminium.energy.EnergyProvider;
import com.laosun.aluminium.energy.StandardEnergyProvider;

/**
 * Current energy, its cap and the provider that decides how it is gained.
 *
 * <p><b>Why this is separate.</b> The bar itself is plain storage; the provider is the character's
 * rule set for what counts as a gain. Keeping the two beside each other makes "no energy bar" and
 * "this provider ignores energy" explicit instead of two independent fields.
 */
public final class EnergyState {

    private double current;
    private double max;
    private EnergyProvider provider = new StandardEnergyProvider();

    /**
     * Energy gained beyond the cap that is kept for later, and how much of it may be kept.
     *
     * <p><b>Why a bank rather than a bigger bar.</b> 「可积攒最多 120 点溢出能量，施放终结技后清空并恢复相应的能量值」
     * (1014 Saber's trace 湖之祝福) is two facts: the bar still fills to {@code max} and no further (an ultimate is castable
     * at the cap and not before), and the excess is <em>remembered</em> so the ultimate can hand it back afterwards. A
     * wider bar would make the second ultimate start charged; a bank keeps the bar's meaning and adds the memory.
     *
     * <p>Zero means "no banking" - the shape every other unit keeps, so a gain that overflows is simply lost. That default
     * is why this could be added without touching the 130-odd files that never overflow.
     */
    private double bank;
    private double bankCap;

    /**
     * @return current energy
     */
    public double current() {
        return current;
    }

    /**
     * @param current the new value
     */
    public void setCurrent(double current) {
        this.current = current;
    }

    /**
     * @return the cap
     */
    public double max() {
        return max;
    }

    /**
     * @param max the cap
     */
    public void setMax(double max) {
        this.max = max;
    }

    /**
     * @return the provider
     */
    public EnergyProvider provider() {
        return provider;
    }

    /**
     * @param provider the provider
     */
    public void setProvider(EnergyProvider provider) {
        this.provider = provider;
    }

    /**
     * @return whether the unit has an energy bar
     */
    public boolean hasBar() {
        return max > 0;
    }

    /**
     * @return whether the bar is full
     */
    public boolean isFull() {
        return hasBar() && current >= max;
    }

    /**
     * Adds what fits under the cap, and <b>banks</b> the rest when this unit has a bank.
     *
     * @param proposed the proposed gain
     * @return the amount actually added to the bar
     */
    public double add(double proposed) {
        double added = Math.min(Math.max(0, max - current), proposed);
        current += added;
        bank(proposed - added);
        return added;
    }

    /**
     * @return how much overflow energy this unit is holding
     */
    public double banked() {
        return bank;
    }

    /**
     * @return how much overflow energy this unit may hold ({@code 0} = none)
     */
    public double bankCap() {
        return bankCap;
    }

    /**
     * Sets the overflow allowance to exactly {@code cap} ("可积攒最多 N 点" / "该上限提高到 M 点").
     *
     * <p>Note: <b>Absolute, not additive</b>, because both sentences state the ALLOWANCE itself (120, then the Eidolon's
     * 「提高到 200 点」 rather than 「提高 80 点」). An additive spelling would have forced the Eidolon to encode 200 − 120, a
     * number that appears nowhere in the data - and the corpus guard {@code EidolonNumbersComeFromTheDataTest} refuses
     * exactly that. Two rules on the same event therefore must be ordered (the trace's 120 first, the Eidolon's 200 after),
     * which is the ordering the shipped file already has.
     *
     * @param cap how many points of overflow may be kept ({@code >= 0}; {@code 0} = no banking)
     */
    public void setBankCap(double cap) {
        bankCap = Math.max(0, cap);
    }

    /**
     * Empties the bank into the bar: 「施放终结技后清空并恢复相应的能量值」.
     *
     * <p>Note: The refund is written straight into the bar rather than through {@code CanHit.gainEnergy}, because that path
     * applies the energy-regeneration rate - and "restores the corresponding amount" is the banked number, not a modified
     * one. The bar's cap still applies (you cannot go past it).
     *
     * @return the amount actually restored
     */
    public double drawBank() {
        double taken = bank;
        bank = 0;
        if (taken <= 0) {
            return 0;
        }
        double added = Math.min(Math.max(0, max - current), taken);
        current += added;
        return added;
    }

    private void bank(double excess) {
        if (excess <= 0 || bankCap <= 0) {
            return;
        }
        bank = Math.min(bankCap, bank + excess);
    }
}
