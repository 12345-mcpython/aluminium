package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code TICK_DOT}: settle one turn of a damage-over-time state on its holder.
 *
 * <p>The engine's own turn boundary already ticks DoTs; this op exists for the sentences that tick one <b>out of
 * turn</b> ("detonate", "one more time"), so it needs the element (which state) and the share.
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
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireElement(effect, op, rule, false);
        TriggerValidator.requirePercent(effect, op, rule);
        TriggerValidator.requireNonZeroPercent(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.tickDot(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
