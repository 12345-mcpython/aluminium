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
final class RemoveResourceOp implements OpHandler<ResourceMoveArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REMOVE_RESOURCE;
    }

    @Override
    public ResourceMoveArgs parse(EffectSpec effect) {
        return new ResourceMoveArgs(
                effect.getResource() == null ? "" : effect.getResource().trim(),
                effect,
                Boolean.TRUE.equals(effect.getSpendAll()),
                Boolean.TRUE.equals(effect.getOverflowOnly()),
                Boolean.TRUE.equals(effect.getToMax()),
                effect.getMaxPerRound(),
                effect.getAmountFromPrevious(),
                effect.getAmountFromEvent(),
                effect.getAmountFromResource(),
                effect.getAmountFromAttr(),
                effect.getAmountPercent(),
                effect.getAmountPercentFromResource(),
                effect.getAmountCap());
    }

    @Override
    public void validate(ResourceMoveArgs args, EffectSpec effect, TriggerSpec rule) {
        TriggerValidator.requireResourceMovement(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, ResourceMoveArgs args) {
        CanHit mover = ctx.target();
        String movedId = args.resource();
        int removed = TriggerInterpreter.removeResource(args, args.magnitude(), ctx.firing(), mover);
        TriggerInterpreter.fireResourceChanged(ctx.battle(), ctx.firing(), mover, movedId, -removed);
    }
}
