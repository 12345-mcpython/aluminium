package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code RAISE_ENERGY_TO}: a <b>floor</b> on the energy bar, not a gain.
 *
 * <p>Energy is raised to the stated share of the maximum and left alone when it is already at or above it; adding a
 * share instead would overshoot. A flat {@code amount} is the other legal spelling (the trace that states the floor in
 * points), and which of the two the sentence uses is recorded in the parsed magnitude rather than re-tested per hit.
 */
final class RaiseEnergyToOp implements OpHandler<MagnitudeArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_ENERGY_TO;
    }

    @Override
    public MagnitudeArgs parse(EffectSpec effect) {
        return new MagnitudeArgs(effect);
    }

    @Override
    public void validate(MagnitudeArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (args.magnitude().getAmount() == null) {
            TriggerValidator.requirePercent(effect, op, rule);
        }
        if (args.magnitude().getPercent() == null && args.magnitude().getValue() == null
                && args.magnitude().getAmount() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " names the level to raise energy TO, so it needs a plain \"percent\", "
                            + "\"amount\" (points) or \"value\": got a share from a cast parameter or a resource "
                            + "instead (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, MagnitudeArgs args) {
        Magnitude magnitude = args.magnitude();
        for (var target : ctx.targets()) {
            double floor = magnitude.getAmount() != null
                    ? magnitude.getAmount()
                    : target.getMaxEnergy() * TriggerMagnitudes.shareOf(magnitude, ctx.firing());
            if (target.getCurrentEnergy() < floor) {
                target.setCurrentEnergy(Math.min(floor, target.getMaxEnergy()));
            }
        }
    }
}
