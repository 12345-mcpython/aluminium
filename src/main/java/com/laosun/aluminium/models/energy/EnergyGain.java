package com.laosun.aluminium.models.energy;

/**
 * One energy grant, as produced by an {@link EnergyProvider}.
 *
 * <p>HSR rule (HSR.md §3.3): {@code 最终获得能量 = 基础获得能量 × (1 + 能量恢复效率%)}.
 * A few sources deliberately bypass the efficiency multiplier (按能量上限百分比回能，
 * 例如流萤「固定恢复等同于自身 60% 能量上限的能量」)，so the two cases are modelled
 * explicitly instead of being conflated into one number.
 *
 * @param amount                base energy before {@code (1 + 能量恢复效率)}
 * @param affectedByEfficiency {@code true} = 走回能效率公式，{@code false} = 定值入账
 */
public record EnergyGain(double amount, boolean affectedByEfficiency) {

    /**
     * A normal grant: scales with {@code 能量恢复效率}.
     *
     * @param amount base energy
     * @return the grant descriptor
     */
    public static EnergyGain normal(double amount) {
        return new EnergyGain(amount, true);
    }

    /**
     * A fixed grant: {@code 能量恢复效率} is ignored.
     *
     * @param amount energy actually granted
     * @return the grant descriptor
     */
    public static EnergyGain fixed(double amount) {
        return new EnergyGain(amount, false);
    }
}
