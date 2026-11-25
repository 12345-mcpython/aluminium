package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code ADD_STACK}: add layers to a counter, optionally overflowing into a second one.
 *
 * <p>{@code SET_STACKS} is the "make it exactly N" twin. Registered here so the op has one dispatch path; its
 * load-time checks are still in {@code TriggerValidator.validateOne}.
 */
final class AddStackOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.ADD_STACK;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.addStack(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
