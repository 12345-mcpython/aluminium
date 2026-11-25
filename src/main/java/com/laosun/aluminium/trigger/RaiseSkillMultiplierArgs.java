package com.laosun.aluminium.trigger;

/**
 * What {@link RaiseSkillMultiplierOp} parsed out of its effect.
 *
 * <p>The optional enemy-count window is what lets one sentence state disjoint tiers ("3+ enemies: x3.4; 2: x2.9; 1:
 * x2.4"), so it is parsed with the number rather than re-read at settlement.
 *
 * @param skill      the slot whose row share is multiplied
 * @param magnitude  the multiplier, as a share (2.4 = x3.4)
 * @param minEnemies the window's lower bound, or {@code null}
 * @param maxEnemies the window's upper bound, or {@code null}
 */
record RaiseSkillMultiplierArgs(String skill, Magnitude magnitude, Integer minEnemies, Integer maxEnemies)
        implements OpArgs {
}
