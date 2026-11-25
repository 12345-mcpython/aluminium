package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code RAISE_RESOURCE_MAX}: raise the resource's <b>declaration</b>, for the rest of the battle.
 *
 * <p>The tier below the overflow allowance {@code RAISE_RESOURCE_CAP} widens - one op must not mean two numbers.
 */
final class RaiseResourceMaxOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_RESOURCE_MAX;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireResource(effect, op, rule);
        TriggerValidator.requirePositiveAmount(effect, op, rule);
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.raiseResourceMax(ctx.effect(), ctx.firing());
    }
}
