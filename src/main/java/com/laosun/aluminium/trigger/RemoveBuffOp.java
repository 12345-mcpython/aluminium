package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code REMOVE_BUFF}: strip named states off the target.
 *
 * <p>The blunt inverse of {@code APPLY_BUFF}; {@code DISPEL} is the class-based one ("remove N negative effects"),
 * which needs no names. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator.validateOne}.
 */
final class RemoveBuffOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REMOVE_BUFF;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.removeBuffs(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
