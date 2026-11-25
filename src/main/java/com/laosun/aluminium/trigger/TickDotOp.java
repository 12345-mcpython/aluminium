package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code TICK_DOT}: settle one turn of a damage-over-time state on its holder.
 *
 * <p>The engine's own turn boundary already ticks DoTs; this op exists for the sentences that tick one <b>out of
 * turn</b> ("detonate", "one more time"), so it needs the element (which state) and the share.
 */
final class TickDotOp implements OpHandler<TickDotArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.TICK_DOT;
    }

    @Override
    public TickDotArgs parse(EffectSpec effect) {
        return new TickDotArgs(effect.getElement() == null ? "" : effect.getElement().trim(), effect);
    }

    @Override
    public void validate(TickDotArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireElement(effect, op, rule, false);
        TriggerValidator.requirePercent(effect, op, rule);
        TriggerValidator.requireNonZeroPercent(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, TickDotArgs args) {
        TriggerEffects.tickDot(ctx, args);
    }
}
