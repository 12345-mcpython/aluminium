package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code RESIST_DEBUFF}: raise one unit's resistance to a class of negative effect.
 *
 * <p>Read on the <b>application</b> path: the resist roll happens when somebody tries to apply the state, so this op
 * writes a fact for a later question rather than doing anything now. Registered here so the op has one dispatch path;
 * its load-time checks are still in {@code TriggerValidator.validateOne}.
 */
final class ResistDebuffOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RESIST_DEBUFF;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.resistDebuff(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
