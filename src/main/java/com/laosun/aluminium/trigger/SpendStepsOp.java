package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code SPEND_STEPS}: consume {@code floor(value / step) * step} of a counter.
 *
 * <p>The paying half of {@code per_steps}, which counts the milestones an effect repeats without spending them; the two
 * are separate because one rule may pay two effects per milestone while the counter is charged once. Registered here
 * so the op has one dispatch path; its load-time checks are still in {@code TriggerValidator.validateOne}.
 */
final class SpendStepsOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SPEND_STEPS;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.spendSteps(ctx.effect(), ctx.firing());
    }
}
