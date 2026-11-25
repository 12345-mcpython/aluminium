package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code SUPER_BREAK}: settle super break damage off the instance being settled.
 *
 * <p>Only legal on {@code DEALING_DAMAGE}: it converts that instance's toughness reduction, so there is no other moment
 * it could be attached to.
 */
final class SuperBreakOp implements OpHandler<MagnitudeArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SUPER_BREAK;
    }

    @Override
    public MagnitudeArgs parse(EffectSpec effect) {
        return new MagnitudeArgs(effect);
    }

    @Override
    public void validate(MagnitudeArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requirePercent(effect, op, rule);
        TriggerValidator.requireNonZeroPercent(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, MagnitudeArgs args) {
        TriggerEffects.superBreak(ctx, args);
    }
}
