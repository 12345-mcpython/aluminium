package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code DISMISS_SUMMON}: take a summoned unit off the field.
 *
 * <p>The inverse of {@code SUMMON}, and unlike {@code DEPART} it removes somebody else's unit rather than the target's
 * own presence. Registered here so the op has one dispatch path; it has <b>no</b> load-time checks today (it falls
 * through to {@code TriggerValidator}'s default arm).
 */
final class DismissSummonOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DISMISS_SUMMON;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.dismissSummon(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
