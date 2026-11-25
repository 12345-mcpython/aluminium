package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code GAIN_SKILL_POINT}: add points to the party's shared pool.
 *
 * <p>The pool belongs to the battle, not to the unit, so this is the one skill-point op that does not name a target.
 * {@code amount} is resolved at fire time ({@code amount_from_skill_param} moves with the skill's level), which is
 * why the parsed shape is empty for now - see {@link OpArgs}.
 */
final class GainSkillPointOp implements OpHandler<MagnitudeArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.GAIN_SKILL_POINT;
    }

    @Override
    public MagnitudeArgs parse(EffectSpec effect) {
        return new MagnitudeArgs(effect);
    }

    @Override
    public void validate(MagnitudeArgs args, EffectSpec effect, TriggerSpec rule) {
        TriggerValidator.requireAmount(effect, op().name(), rule);
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, MagnitudeArgs args) {
        ctx.battle().gainSkillPoint((int) Math.round(
                TriggerInterpreter.scaledAmount(args.magnitude(), ctx.firing())));
    }
}
