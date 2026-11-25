package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code GAIN_RESOURCE}: credit a counter, clamped by its cap and overflow rules, and announce the move.
 *
 * <p>The unit measured is the one that <b>receives</b>, not the rule's owner: an effect may grant to somebody else, and
 * measuring the owner read 0 on both sides for a teammate's counter.
 */
final class GainResourceOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.GAIN_RESOURCE;
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
        TriggerInterpreter.gainResource(ctx.effect(), ctx.firing(), ctx.previousCredited(), mover);
        TriggerInterpreter.fireResourceChanged(ctx.battle(), ctx.firing(), mover, movedId,
                TriggerInterpreter.resourceAmount(ctx.battle(), mover, movedId) - before);
    }
}
