package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code APPLY_DOT}: a damage-over-time state with its magnitude fixed once, at application.
 *
 * <p>{@code BOOST_DOT} is the other half: it multiplies the per-settlement damage of DoTs already applied, which this
 * op cannot do because it fixes the amount when the state lands. Registered here so the op has one dispatch path; its
 * load-time checks are still in {@code TriggerValidator.validateOne}.
 */
final class ApplyDotOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.APPLY_DOT;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.applyDot(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
