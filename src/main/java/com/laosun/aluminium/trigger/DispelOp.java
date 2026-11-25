package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code DISPEL}: remove up to {@code amount} negative effects, newest first.
 *
 * <p>What counts as negative is {@code AbstractBuff.isDebuff()}, decided per buff class - so a state applied by
 * {@code APPLY_BUFF} is dispellable only if its class says so. Registered here so the op has one dispatch path; its
 * load-time checks are still in {@code TriggerValidator.validateOne}.
 */
final class DispelOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DISPEL;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.dispel(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
