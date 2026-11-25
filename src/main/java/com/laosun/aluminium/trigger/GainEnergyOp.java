package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code GAIN_ENERGY}: add energy to a unit's bar, with the overflow bank as the ceiling.
 *
 * <p>The bank is per-unit state, not this op's business: {@code GAIN_ENERGY} writes the bar and
 * {@code TriggerInterpreter.gainEnergy} decides what happens to the excess. Registered here so the op has one dispatch
 * path; its load-time checks are still in {@code TriggerValidator}.
 */
final class GainEnergyOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.GAIN_ENERGY;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.gainEnergy(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
