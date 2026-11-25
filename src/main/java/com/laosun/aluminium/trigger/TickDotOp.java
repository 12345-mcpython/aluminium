package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code TICK_DOT}: settle one turn of a damage-over-time state on its holder.
 *
 * <p>The engine's own turn boundary already ticks DoTs; this op exists for the sentences that tick one <b>out of
 * turn</b> ("detonate", "one more time"). Registered here so the op has one dispatch path; its load-time checks are
 * still in {@code TriggerValidator.validateOne}.
 */
final class TickDotOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.TICK_DOT;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.tickDot(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
