package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code RESET_RULE_CHANCE}: put a decaying rule's probability back to the number it was written with.
 *
 * <p>The decay itself lives on the combatant, keyed by rule id, so this is the only op that has to name another rule
 * by id. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator.validateOne}.
 */
final class ResetRuleChanceOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RESET_RULE_CHANCE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        CanHit owner = ctx.firing().owner();
        if (owner != null) {
            owner.resetRuleChance(ctx.effect().getRule().trim());
        }
    }
}
