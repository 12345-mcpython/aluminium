package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code SUMMON_SERVANT}: bring a servant (a second, non-memosprite kind of summon) onto the field.
 *
 * <p>Distinct from {@code SUMMON}, which brings the owner's own memosprite: a servant is authored per character and may
 * be several. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator}.
 */
final class SummonServantOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SUMMON_SERVANT;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        ctx.battle().summonServant(TriggerMagnitudes.requireCharacterOwner(ctx.effect(), ctx.firing()));
    }
}
