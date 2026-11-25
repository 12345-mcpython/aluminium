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
final class SpendResourceOp implements OpHandler<ResourceMoveArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SPEND_RESOURCE;
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
        int before = TriggerInterpreter.resourceAmount(ctx.battle(), mover, movedId);
        TriggerInterpreter.spendResource(args, args.magnitude(), ctx.firing(), mover);
        TriggerInterpreter.fireResourceChanged(ctx.battle(), ctx.firing(), mover, movedId,
                TriggerInterpreter.resourceAmount(ctx.battle(), mover, movedId) - before);
    }
}
