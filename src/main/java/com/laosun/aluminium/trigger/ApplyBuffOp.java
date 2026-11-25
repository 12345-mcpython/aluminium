package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code APPLY_BUFF}: put a unit into a <b>named state</b>.
 *
 * <p>What the state <i>does</i> is separate effects conditioned on {@code has_state}, which keeps "what the state is"
 * apart from "what it changes"; a plain stat buff is {@code MODIFY_ATTR}. Registered here so the op has one dispatch
 * path; its load-time checks are still in {@code TriggerValidator.validateOne}.
 */
final class ApplyBuffOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.APPLY_BUFF;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.applyState(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
