package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code BOOST_DOT}: raise the per-settlement damage of damage-over-time states already on the target.
 *
 * <p>{@code APPLY_DOT} fixes an amount once, so it cannot say "the burn you already have hits 40% harder"; that is what
 * this op exists for. Registered here so the op has one dispatch path; it has <b>no</b> load-time checks today (it
 * falls through to {@code TriggerValidator}'s default arm).
 */
final class BoostDotOp implements OpHandler<BoostDotArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.BOOST_DOT;
    }

    @Override
    public BoostDotArgs parse(EffectSpec effect) {
        return new BoostDotArgs(effect, effect.getElement());
    }

    @Override
    public void validate(BoostDotArgs args, EffectSpec effect, TriggerSpec rule) {
        // This op has no arm of its own in TriggerValidator, so this was the default check it used to get there.
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, BoostDotArgs args) {
        TriggerInterpreter.boostDot(ctx, args);
    }
}
