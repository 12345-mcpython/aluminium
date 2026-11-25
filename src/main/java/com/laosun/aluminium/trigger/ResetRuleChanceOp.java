package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code RESET_RULE_CHANCE}: put a decaying rule's probability back to the number it was written with.
 *
 * <p>It names the rule for the same reason {@code MODIFY_RULE} does - "reset the probability" without saying whose has
 * no subject. Whether the named rule really <b>decays</b> is checked in {@code TriggerTable.validateAmendments}, the
 * only place that can see the whole file.
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
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireRuleReference(effect, op, rule);
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        CanHit owner = ctx.firing().owner();
        if (owner != null) {
            owner.resetRuleChance(ctx.effect().getRule().trim());
        }
    }
}
