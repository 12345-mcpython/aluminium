package com.laosun.aluminium.trigger;

/**
 * What {@link AddDamageOp} parsed out of its effect.
 *
 * @param magnitude   how much is added
 * @param afterZones  whether the amount lands on the SETTLED instance (bypassing every zone) or on the base layer
 * @param damageType  the damage type it is scoped to, or {@code null} for every type
 * @param countedLaughs the explicit Elation laugh count, or {@code null} to read the battle's own multiplier
 */
record AddDamageArgs(Magnitude magnitude, boolean afterZones, String damageType, Double countedLaughs)
        implements OpArgs {
}
