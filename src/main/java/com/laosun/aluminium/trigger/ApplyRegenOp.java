package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code APPLY_REGEN}: a heal-over-time state.
 *
 * <p>{@code HEAL} is the instant kind; a regeneration is ticked on the holder's turn by the engine's own boundary, so
 * this op only installs the state. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator.validateOne}.
 */
final class ApplyRegenOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.APPLY_REGEN;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.applyRegen(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
