package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code RESET_RESOURCE_CAP}: start a new round for the per-round allowances a resource hands out.
 *
 * <p>Nothing about a counter changes - only what this round may still grant, which is what {@code max_per_round}
 * charges against. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator.validateOne}.
 */
final class ResetResourceCapOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RESET_RESOURCE_CAP;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        CanHit owner = ctx.firing().owner();
        if (owner != null) {
            owner.getResources().resetRoundAllowances();
        }
    }
}
