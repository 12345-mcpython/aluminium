package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code SUMMON_SERVANT}: bring a servant (a second, non-memosprite kind of summon) onto the field.
 *
 * <p>Distinct from {@code SUMMON}, which brings the owner's own memosprite: a servant is authored per character and may
 * be several. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator}.
 */
final class SummonServantOp implements OpHandler<MagnitudeArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SUMMON_SERVANT;
    }

    @Override
    public MagnitudeArgs parse(EffectSpec effect) {
        return new MagnitudeArgs(effect);
    }

    @Override
    public void validate(MagnitudeArgs args, EffectSpec effect, TriggerSpec rule) {
        // This op has no arm of its own in TriggerValidator, so this was the default check it used to get there.
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, MagnitudeArgs args) {
        ctx.battle().summonServant(TriggerMagnitudes.requireCharacterOwner(args.magnitude(), ctx.firing()));
    }
}
