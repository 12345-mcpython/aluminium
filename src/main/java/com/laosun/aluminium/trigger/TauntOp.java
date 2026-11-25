package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code TAUNT}: make an enemy aim at the named unit.
 *
 * <p>A state on the <b>enemy</b>, read by the targeting policy, not a stat. Registered here so the op has one dispatch
 * path; its load-time checks are still in {@code TriggerValidator.validateOne}.
 */
final class TauntOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.TAUNT;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.taunt(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
