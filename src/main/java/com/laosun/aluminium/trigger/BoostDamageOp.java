package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code BOOST_DAMAGE}: raise the instance being settled, when the rule fires on {@code DEALING_DAMAGE}.
 *
 * <p>No duration, no target and no buff: the instance is the state, so nothing persists and nothing can leak into the
 * next hit. Registered here so the op has one dispatch path; its load-time checks are still in {@code TriggerValidator}.
 */
final class BoostDamageOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.BOOST_DAMAGE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.boostDamage(ctx.effect(), ctx.firing());
    }
}
