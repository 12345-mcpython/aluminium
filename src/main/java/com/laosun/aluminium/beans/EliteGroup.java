package com.laosun.aluminium.beans;

/**
 * Elite-level enemy scaling ratios for each combat stat.
 *
 * <p>Deserialized from elation_basic_level_damage.json. The ratios
 * are applied as multipliers to base enemy stats.
 */
public record EliteGroup(double healthRatio, double attackRatio, double defenceRatio, double speedRatio,
                         double stanceRatio) {
}
