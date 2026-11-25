package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code RAISE_SKILL_POINT_CAP}: move the pool's ceiling, positive or negative.
 *
 * <p>Unlike {@code GAIN_SKILL_POINT}, a negative amount is meaningful here: it takes a granted bonus back. Zero is
 * refused, because it would be a number that does nothing.
 */
final class RaiseSkillPointCapOp implements OpHandler<MagnitudeArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_SKILL_POINT_CAP;
    }

    @Override
    public MagnitudeArgs parse(EffectSpec effect) {
        return new MagnitudeArgs(effect);
    }

    @Override
    public void validate(MagnitudeArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (args.magnitude().getValue() == null
                && (args.magnitude().getAmount() == null || args.magnitude().getAmount() == 0)) {
            throw new IllegalArgumentException(
                    "Op " + op + " changes the skill point cap, so \"amount\" is a non-zero number of points"
                            + " (negative takes a bonus away): got "
                            + (args.magnitude().getAmount() == null ? "nothing" : args.magnitude().getAmount())
                            + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, MagnitudeArgs args) {
        Magnitude magnitude = args.magnitude();
        int points = magnitude.getValue() != null
                ? (int) Math.round(TriggerMagnitudes.valueOf(magnitude, ctx.firing()))
                : (int) Math.round(magnitude.getAmount());
        ctx.battle().getSkillPointPolicy().raiseMax(points);
    }
}
