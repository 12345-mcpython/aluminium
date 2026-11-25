package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code DRAW_ENERGY_BANK}: empty the banked overflow back into the energy bar.
 *
 * <p>A separate op from {@code GAIN_ENERGY} because the amount is not stated anywhere - it is whatever the unit
 * happened to bank. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator}.
 */
final class DrawEnergyBankOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DRAW_ENERGY_BANK;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.energyBankDraw(ctx.effect(), ctx.firing());
    }
}
