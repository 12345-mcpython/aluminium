package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code REPLACE_SKILL}: swap one of the owner's skill slots, or put the original back.
 *
 * <p>The two spellings must be paired - {@code { remove: true }} is the only way back, because a replaced slot has no
 * other record of what it used to be. Registered here so the op has one dispatch path; its load-time checks are still
 * in {@code TriggerValidator.validateOne}.
 */
final class ReplaceSkillOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REPLACE_SKILL;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.replaceSkill(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
