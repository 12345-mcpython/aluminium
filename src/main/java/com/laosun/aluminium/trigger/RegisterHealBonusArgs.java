package com.laosun.aluminium.trigger;

/**
 * What {@link RegisterHealBonusOp} parsed out of its effect.
 *
 * @param magnitude        the bonus itself, as a share
 * @param scaleByMissingHp whether the bonus grows with how hurt the recipient is - which is also why such a bonus may
 *                         omit the {@code target_when} condition: at full HP it grants nothing anyway
 */
record RegisterHealBonusArgs(Magnitude magnitude, boolean scaleByMissingHp) implements OpArgs {
}
