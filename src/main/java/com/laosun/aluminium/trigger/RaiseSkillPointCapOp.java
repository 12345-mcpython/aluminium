package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code RAISE_SKILL_POINT_CAP}: move the pool's ceiling, positive or negative.
 *
 * <p>Unlike {@code GAIN_SKILL_POINT}, a negative amount is meaningful here: it takes a granted bonus back, which is
 * how a rule states "for this battle the cap is 7" as a +2 and a later -2. Zero is refused, because it would be a
 * number that does nothing.
 */
final class RaiseSkillPointCapOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_SKILL_POINT_CAP;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        if (effect.getValue() == null && (effect.getAmount() == null || effect.getAmount() == 0)) {
            throw new IllegalArgumentException(
                    "Op " + op().name() + " changes the skill point cap, so \"amount\" is a non-zero number of points"
                            + " (negative takes a bonus away): got "
                            + (effect.getAmount() == null ? "nothing" : effect.getAmount())
                            + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        EffectSpec effect = ctx.effect();
        int points = effect.getValue() != null
                ? (int) Math.round(TriggerMagnitudes.valueOf(effect, ctx.firing()))
                : (int) Math.round(effect.getAmount());
        ctx.battle().getSkillPointPolicy().raiseMax(points);
    }
}
