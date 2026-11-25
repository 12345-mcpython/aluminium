package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code REMOVE_RESOURCE}: take a counter back, clamped rather than refused.
 *
 * <p>Deliberately not {@code SPEND_RESOURCE}: this pool is meant to be consumed, so a revocation with less than the
 * stated amount removes what is there instead of erroring. It may also name a <b>share</b> of one of the owner's own
 * counters ({@code scale: self_resource:<id>} + {@code percent}) instead of a flat amount.
 */
final class RemoveResourceOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REMOVE_RESOURCE;
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
        int removed = TriggerInterpreter.removeResource(ctx.effect(), ctx.firing(), mover);
        TriggerInterpreter.fireResourceChanged(ctx.battle(), ctx.firing(), mover, movedId, -removed);
    }
}
