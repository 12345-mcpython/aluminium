package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code DELEGATE_DAMAGE}: the cast in progress must not deal its own damage; somebody else's does.
 *
 * <p>The inverse of a damage bonus: it removes the instance rather than scaling it, which is why it is an op and not a
 * condition on {@code DAMAGE}. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator}.
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
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.delegateDamage(ctx.effect(), ctx.firing());
    }
}
