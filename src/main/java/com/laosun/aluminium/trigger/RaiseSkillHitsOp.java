package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code RAISE_SKILL_HITS}: add damage segments to a data slot, for the rest of the battle.
 *
 * <p>The "segments" are the row's own; this only changes how many of them settle. A negative amount takes them back,
 * which is how a rule states a duration. Registered here so the op has one dispatch path; its load-time checks are
 * still in {@code TriggerValidator.validateOne}.
 */
final class RaiseSkillHitsOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_SKILL_HITS;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.raiseSkillHits(ctx.effect(), ctx.firing());
    }
}
