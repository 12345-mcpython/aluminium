package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code BOOST_TOUGHNESS}: raise the toughness reduction this instance will cause.
 *
 * <p>A multiplier on the reduction, read at one place ({@code SkillExecutor.applyStanceDamage}) rather than inside
 * {@code Battle.reduceToughness}, which enemy skills and the demo also call. A {@code scale} is <b>refused</b>: the
 * magnitude is read as a plain {@code percent} when the buff is built, so a scale would load and do nothing.
 */
final class BoostToughnessOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.BOOST_TOUGHNESS;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (effect.getScale() != null && !effect.getScale().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"scale\", which this op does NOT read yet (it takes a plain \"percent\"); "
                            + "stating it would silently do nothing (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requirePercent(effect, op, rule);
        TriggerValidator.requireNonZeroPercent(effect, op, rule);
        TriggerValidator.requireDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.boostToughness(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
