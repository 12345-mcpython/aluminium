package com.laosun.aluminium.trigger;

/**
 * What {@link BoostToughnessOp} parsed out of its effect.
 *
 * @param magnitude  the multiplier on the toughness reduction this instance will cause
 * @param turns      the buff's lifetime
 * @param perStack   the counter whose layers multiply the share, or {@code null}
 * @param perStackCap the ceiling on those layers
 */
record BoostToughnessArgs(Magnitude magnitude, Integer turns, String perStack, Integer perStackCap)
        implements OpArgs {
}
