package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code SUPER_BREAK}: settle super break damage off the instance being settled.
 *
 * <p>Only legal on {@code DEALING_DAMAGE}: it reads that instance's toughness reduction, so there is no other moment it
 * could be attached to. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator.validateOne}.
 */
final class SuperBreakOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SUPER_BREAK;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.superBreak(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
