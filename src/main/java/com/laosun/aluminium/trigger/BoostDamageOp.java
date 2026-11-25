package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;

/**
 * {@code BOOST_DAMAGE}: raise the instance being settled, when the rule fires on {@code DEALING_DAMAGE}.
 *
 * <p>No duration, no target and no buff: the instance is the state, so nothing persists and nothing can leak into the
 * next hit. Being tied to the instance is why the event is checked at load time.
 */
final class BoostDamageOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.BOOST_DAMAGE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requirePercent(effect, op, rule);
        TriggerValidator.requireNonZeroPercent(effect, op, rule);
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        TriggerValidator.requireEvent(rule, op, TriggerEvent.DEALING_DAMAGE);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.boostDamage(ctx.effect(), ctx.firing());
    }
}
