package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code RESET_RULE_CHANCE}: put a decaying rule's probability back to the number it was written with.
 *
 * <p>It names the rule for the same reason {@code MODIFY_RULE} does - "reset the probability" without saying whose has
 * no subject. Whether the named rule really <b>decays</b> is checked in {@code TriggerTable.validateAmendments}, the
 * only place that can see the whole file. The id is parsed once ({@link ResetRuleChanceArgs}); the run-time half no
 * longer reads the bean.
 */
final class ResetRuleChanceOp implements OpHandler<ResetRuleChanceArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RESET_RULE_CHANCE;
    }

    @Override
    public ResetRuleChanceArgs parse(EffectSpec effect) {
        return new ResetRuleChanceArgs(effect.getRule() == null ? "" : effect.getRule().trim());
    }

    @Override
    public void validate(ResetRuleChanceArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireRuleReference(effect, op, rule);
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, ResetRuleChanceArgs args) {
        CanHit owner = ctx.firing().owner();
        if (owner != null) {
            owner.resetRuleChance(args.rule());
        }
    }
}
