package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code RAISE_ENERGY_BANK}: set how much energy may overflow into the bank.
 *
 * <p>About the <b>energy bar</b>, not a resource - resource overflow is {@code RAISE_RESOURCE_CAP}, and the two must
 * not share a spelling. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator}.
 */
final class RaiseEnergyBankOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_ENERGY_BANK;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.energyBankCap(ctx.effect(), ctx.firing());
    }
}
