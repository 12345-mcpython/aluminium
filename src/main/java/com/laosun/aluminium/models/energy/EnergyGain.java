package com.laosun.aluminium.models.energy;

/**
 * One energy grant, as produced by an {@link EnergyProvider}.
 *
 * <p>HSR rule (HSR.md §3.3): {@code final energy gained = base energy gained × (1 + energy
 * regeneration rate %)}.
 * A few sources deliberately bypass the efficiency multiplier (energy gain as a percentage of max
 * energy, e.g. Firefly's 「restores a fixed amount of energy equal to 60% of her own max energy」),
 * so the two cases are modelled explicitly instead of being conflated into one number.
 *
 * @param amount               base energy before {@code (1 + energy regeneration rate)}
 * @param affectedByEfficiency {@code true} = goes through the energy-regeneration formula,
 *                             {@code false} = credited as a fixed value
 */
public record EnergyGain(double amount, boolean affectedByEfficiency) {

    /**
     * A normal grant: scales with the {@code energy regeneration rate}.
     *
     * @param amount base energy
     * @return the grant descriptor
     */
    public static EnergyGain normal(double amount) {
        return new EnergyGain(amount, true);
    }

    /**
     * A fixed grant: the {@code energy regeneration rate} is ignored.
     *
     * @param amount energy actually granted
     * @return the grant descriptor
     */
    public static EnergyGain fixed(double amount) {
        return new EnergyGain(amount, false);
    }
}
