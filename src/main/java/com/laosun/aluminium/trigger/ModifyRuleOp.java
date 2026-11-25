package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code MODIFY_RULE}: amend another rule's own numbers (its amount, percent, turns or stack cap).
 *
 * <p>Only legal on {@code BATTLE_START}, {@code CAST_SETUP} and {@code SUMMONED}, because it rewrites a rule that will
 * fire later; the amendment is stored per combatant, so it cannot leak into another battle. Registered here so the op
 * has one dispatch path; its load-time checks are still in {@code TriggerValidator.validateOne}.
 */
final class ModifyRuleOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.MODIFY_RULE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.modifyRule(ctx.effect(), ctx.firing());
    }
}
