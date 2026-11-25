package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code RAISE_RESOURCE_CAP}: widen a declared resource's <b>overflow</b> allowance.
 *
 * <p>Not {@code RAISE_RESOURCE_MAX}, which raises the declaration itself. Registered here so the op has one dispatch
 * path; its load-time checks are still in {@code TriggerValidator.validateOne}.
 */
final class RaiseResourceCapOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_RESOURCE_CAP;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.raiseResourceCap(ctx.effect(), ctx.firing());
    }
}
