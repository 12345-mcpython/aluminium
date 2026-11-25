package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code RAISE_ENERGY_TO}: a <b>floor</b> on the energy bar, not a gain.
 *
 * <p>Energy is raised to the stated share of the maximum and left alone when it is already at or above it; adding a
 * share instead would overshoot. A flat {@code amount} is the other legal spelling (the trace that states the floor in
 * points). Registered here so the op has one dispatch path; its load-time checks are still in {@code TriggerValidator}.
 */
final class RaiseEnergyToOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_ENERGY_TO;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        for (CanHit target : ctx.targets()) {
            double floor = ctx.effect().getAmount() != null
                    ? ctx.effect().getAmount()
                    : target.getMaxEnergy() * ctx.share();
            if (target.getCurrentEnergy() < floor) {
                target.setCurrentEnergy(Math.min(floor, target.getMaxEnergy()));
            }
        }
    }
}
