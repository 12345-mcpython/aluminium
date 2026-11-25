package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code REMOVE_STACK}: take layers of one attribute's modifier off the target.
 *
 * <p>Removing nothing is not an error - the sentence that says "one stack per turn" fires every turn whether or not
 * there is one. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator.validateOne}.
 */
final class RemoveStackOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REMOVE_STACK;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.removeStacks(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
