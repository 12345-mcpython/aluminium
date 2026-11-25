package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code REMOVE_STATE}: drop one named state, without the class question {@code DISPEL} asks.
 *
 * <p>Needs no battlefield handle - removing a state is a fact about the unit, which is why
 * {@code TriggerEffects.removeState} takes only the effect and the context. Registered here so the op has one dispatch
 * path; its load-time checks are still in {@code TriggerValidator.validateOne}.
 */
final class RemoveStateOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REMOVE_STATE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.removeState(ctx.effect(), ctx.firing());
    }
}
