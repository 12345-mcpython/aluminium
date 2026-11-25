package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code RAISE_RESOURCE_CAP}: widen a declared resource's <b>overflow</b> allowance.
 *
 * <p>Not {@code RAISE_RESOURCE_MAX}, which raises the declaration itself. {@code unbounded} and a count are refused
 * together: "no upper limit" and "by this many more" are different claims.
 */
final class RaiseResourceCapOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_RESOURCE_CAP;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireResource(effect, op, rule);
        if (Boolean.TRUE.equals(effect.getUnbounded())) {
            if (effect.getAmount() != null || effect.getValue() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states both \"unbounded\" and a count (\"amount\" / \"value\"): "
                                + "\"no upper limit\" and \"by this many more\" are different claims "
                                + "(source: " + rule.getSource() + ")");
            }
        } else if (effect.getValue() == null && (effect.getAmount() == null || effect.getAmount() <= 0)) {
            throw new IllegalArgumentException(
                    "Op " + op + " widens a resource's overflow, so \"amount\" is a positive count of points: got "
                            + (effect.getAmount() == null ? "nothing" : effect.getAmount())
                            + " (source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.raiseResourceCap(ctx.effect(), ctx.firing());
    }
}
