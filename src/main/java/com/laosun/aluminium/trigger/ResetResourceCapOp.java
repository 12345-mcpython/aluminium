package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code RESET_RESOURCE_CAP}: start a new round for the per-round allowances a resource hands out.
 *
 * <p>Nothing about a counter changes - only what this round may still grant, which is what {@code max_per_round}
 * charges against. The counter must be named for the same reason: "reset my allowances" without saying which one is a
 * rule whose subject is missing.
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
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireResource(effect, op, rule);
        TriggerValidator.requireNoDuration(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        CanHit owner = ctx.firing().owner();
        if (owner != null) {
            owner.getResources().resetRoundAllowances();
        }
    }
}
