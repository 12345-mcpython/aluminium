package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code ADD_DAMAGE}: an extra instance added on top of one already settled.
 *
 * <p>With {@code after_zones} it adds an absolute amount to the settled instance (no crit, no boost, no defence, no
 * resistance zone); without it the amount is part of the base. Registered here so the op has one dispatch path; its
 * load-time checks are still in {@code TriggerValidator}.
 */
final class AddDamageOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.ADD_DAMAGE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.addDamageFlat(ctx.effect(), ctx.firing());
    }
}
