package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code GAIN_ENERGY}: add energy to a unit's bar, with the overflow bank as the ceiling.
 *
 * <p>Three legal shapes, and the check exists to keep them apart: a plain share of max energy, a cast parameter
 * ({@code #2} of the skill that produced the effect), and {@code fixed} + a bare share - "the stated share of the
 * RECIPIENT's own maximum, untouched by the regen rate". A {@code fixed} number that also states {@code scale} is
 * refused, because a fixed number and a share are different magnitudes.
 */
final class GainEnergyOp implements OpHandler<MagnitudeArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.GAIN_ENERGY;
    }

    @Override
    public MagnitudeArgs parse(EffectSpec effect) {
        return new MagnitudeArgs(effect);
    }

    @Override
    public void validate(MagnitudeArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        boolean fixedShare = Boolean.TRUE.equals(effect.getFixed()) && effect.getAmount() == null
                && (effect.getPercent() != null || effect.getPercentFrom() != null
                || effect.getValue() != null)
                && (effect.getScale() == null || effect.getScale().isBlank());
        if (fixedShare) {
            TriggerValidator.requireNoStackArguments(effect, op, rule);
        } else if (TriggerInterpreter.isCastParamScale(effect)) {
            TriggerValidator.requireNoStackArguments(effect, op, rule);
        } else {
            TriggerValidator.requireAmountOrScale(effect, op, rule, TriggerValidator.ENERGY_SCALES, "max energy");
            TriggerValidator.requireNoStackArguments(effect, op, rule);
        }
        if (Boolean.TRUE.equals(effect.getFixed())) {
            // 「固定恢复 N 点」 states a NUMBER, so it cannot also be a share: refusing the pair keeps "fixed" from
            // silently deciding which of two magnitudes wins.
            if (effect.getAmount() == null && !fixedShare) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"fixed\", so \"amount\" must be the stated number of points: got "
                                + (effect.getScale() == null ? "nothing" : "\"scale\": \"" + effect.getScale()
                                + "\"") + " (source: " + rule.getSource() + ")");
            }
            if (effect.getScale() != null && !effect.getScale().isBlank()) {
                throw new IllegalArgumentException(
                        "Op " + op + " states both \"fixed\" and \"scale\": a fixed number and a share of "
                                + "something are different magnitudes (source: " + rule.getSource() + ")");
            }
        }
    }

    @Override
    public void apply(OpContext ctx, MagnitudeArgs args) {
        TriggerInterpreter.gainEnergy(ctx, args);
    }
}
