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
final class DelegateDamageOp implements OpHandler<DelegateDamageArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DELEGATE_DAMAGE;
    }

    @Override
    public DelegateDamageArgs parse(EffectSpec effect) {
        return new DelegateDamageArgs(effect.getSkill() == null ? "" : effect.getSkill().trim());
    }

    @Override
    public void validate(DelegateDamageArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireSkill(effect, op, rule);
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        TriggerValidator.requireNoTarget(effect, op, rule);
        TriggerValidator.requireEvent(rule, op, TriggerEvent.CAST_SETUP);
    }

    @Override
    public void apply(OpContext ctx, DelegateDamageArgs args) {
        TriggerInterpreter.delegateDamage(ctx, args);
    }
}
