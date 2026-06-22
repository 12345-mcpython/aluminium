package com.laosun.aluminium.beans;

/**
 * Hard-level enemy scaling ratios including effect hit rate and resistance.
 *
 * <p>Deserialized from hard_level_group.json. Extends the basic scaling
 * ratios with additional effect-related multipliers.
 */
public record HardLevelGroup(double attackRatio, double defenceRatio, double healthRatio, double speedRatio,
                             double stanceRatio, double effectHitRateRatio,
                             double effectResistanceRatio) {
}
