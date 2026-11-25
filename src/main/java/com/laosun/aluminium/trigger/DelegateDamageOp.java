package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;

/**
 * {@code DELEGATE_DAMAGE}: the cast in progress must not deal its own damage; somebody else's does.
 *
 * <p>Only the slot being cast is named. Everything else about the swing - who delivers it and with what numbers -
 * belongs to the rule that <b>does</b> deliver it; this op only says "not by me". Hence the {@code CAST_SETUP} event and
 * the absence of a target, a duration and stack arguments.
 */
final class DelegateDamageOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DELEGATE_DAMAGE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireSkill(effect, op, rule);
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        TriggerValidator.requireNoTarget(effect, op, rule);
        TriggerValidator.requireEvent(rule, op, TriggerEvent.CAST_SETUP);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.delegateDamage(ctx.effect(), ctx.firing());
    }
}
