package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code APPLY_CONTROL}: a control state, resisted through the effect's own chance pipeline.
 *
 * <p>The negative-effect classes are decided per buff class ({@code AbstractBuff.isDebuff()}), so "control" is a
 * property of what is applied rather than of this op. Registered here so the op has one dispatch path; its load-time
 * checks are still in {@code TriggerValidator.validateOne}.
 */
final class ApplyControlOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.APPLY_CONTROL;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.applyControl(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
