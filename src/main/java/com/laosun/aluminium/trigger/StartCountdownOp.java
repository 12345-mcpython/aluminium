package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code START_COUNTDOWN}: put a countdown unit on the action bar.
 *
 * <p>A countdown is a combatant that acts once and then leaves, which is the engine's way of saying "in N of its turns"
 * without inventing a timer that nothing ticks. Registered here so the op has one dispatch path; its load-time checks
 * are still in {@code TriggerValidator.validateOne}.
 */
final class StartCountdownOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.START_COUNTDOWN;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.startCountdown(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
