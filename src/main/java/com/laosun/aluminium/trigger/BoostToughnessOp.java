package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code BOOST_TOUGHNESS}: restore toughness to a unit, a whole point at a time.
 *
 * <p>The inverse of the reduction every attack performs, and the one op whose `per_stack` reads the stacks of the
 * effect's own holder. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator.validateOne}.
 */
final class BoostToughnessOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.BOOST_TOUGHNESS;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.boostToughness(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
