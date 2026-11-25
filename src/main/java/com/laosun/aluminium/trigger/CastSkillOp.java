package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code CAST_SKILL}: perform a whole skill as an effect of a rule.
 *
 * <p>The escape hatch for "immediately casts X": it runs the normal cast pipeline, so the skill's own zones, targets and
 * misses all apply. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator}.
 */
final class CastSkillOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.CAST_SKILL;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.castSkill(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
