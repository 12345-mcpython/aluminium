package com.laosun.aluminium.models;


import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.utils.AttributeBuilder;

/**
 * Extra flat and percentage bonuses added to a character's base stats.
 *
 * <p>Represents static bonuses from sources like handguards, traces,
 * or other promotion-related stat increases that are applied directly
 * to the final attributes.
 */
public record ExtraBasicPromote(double health, double defence, double attack, double speed, double healthPercent,
                                double defencePercent, double attackPercent, double speedPercent) {
    /**
     * Creates an empty promotion with all zeros.
     */
    public ExtraBasicPromote() {
        this(0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Applies all bonus values to the given attribute builder.
     *
     * @param atb the builder to append to
     */
    public void appendTo(AttributeBuilder atb) {
        atb.addPure(AttributeType.HEALTH, health, DoubleValue.Modifier.ModifierSource.EXTRA);
        atb.addPure(AttributeType.DEFENCE, defence, DoubleValue.Modifier.ModifierSource.EXTRA);
        atb.addPure(AttributeType.ATTACK, attack, DoubleValue.Modifier.ModifierSource.EXTRA);
        atb.addPure(AttributeType.SPEED, speed, DoubleValue.Modifier.ModifierSource.EXTRA);
        atb.addPercent(AttributeType.HEALTH_PERCENT, healthPercent, DoubleValue.Modifier.ModifierSource.EXTRA);
        atb.addPercent(AttributeType.DEFENCE_PERCENT, defencePercent, DoubleValue.Modifier.ModifierSource.EXTRA);
        atb.addPercent(AttributeType.ATTACK_PERCENT, attackPercent, DoubleValue.Modifier.ModifierSource.EXTRA);
        atb.addPercent(AttributeType.SPEED_PERCENT, speedPercent, DoubleValue.Modifier.ModifierSource.EXTRA);
    }
}
