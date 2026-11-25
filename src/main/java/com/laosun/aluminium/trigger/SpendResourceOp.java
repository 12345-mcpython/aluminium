package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code SPEND_RESOURCE}: pay a counter, refusing when the pool is short.
 *
 * <p>The strict half of the resource pair; {@code REMOVE_RESOURCE} is the clamping one, because a revocation must not
 * fail when there is less to take.
 */
final class SpendResourceOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SPEND_RESOURCE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        TriggerValidator.requireResourceMovement(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        CanHit mover = ctx.target();
        String movedId = ctx.effect().getResource();
        int before = TriggerInterpreter.resourceAmount(ctx.battle(), mover, movedId);
        TriggerInterpreter.spendResource(ctx.effect(), ctx.firing(), mover);
        TriggerInterpreter.fireResourceChanged(ctx.battle(), ctx.firing(), mover, movedId,
                TriggerInterpreter.resourceAmount(ctx.battle(), mover, movedId) - before);
    }
}
