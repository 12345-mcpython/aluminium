package com.laosun.aluminium.models;

import com.laosun.aluminium.models.energy.EnergyProvider;
import com.laosun.aluminium.models.energy.StandardEnergyProvider;

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
     * Adds what fits under the cap.
     *
     * @param proposed the proposed gain
     * @return the amount actually added
     */
    public double add(double proposed) {
        double added = Math.min(Math.max(0, max - current), proposed);
        current += added;
        return added;
    }
}
