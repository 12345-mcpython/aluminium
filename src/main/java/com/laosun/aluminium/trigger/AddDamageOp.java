package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;

/**
 * {@code ADD_DAMAGE}: an extra instance added on top of one already settled.
 *
 * <p>With {@code after_zones} it adds an absolute amount to the settled instance (no crit, no boost, no defence, no
 * resistance zone); without it the amount is part of the base. A COUNT is not an amount: {@code times} counts
 * instances, and this op adds one amount - a {@code times} here would be read by nobody.
 */
final class AddDamageOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.ADD_DAMAGE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireEvent(rule, op, TriggerEvent.DEALING_DAMAGE);
        TriggerMagnitudes.scaleAttribute(effect, op, rule);
        TriggerValidator.requirePercent(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        if (effect.getTimes() != null || effect.getTimesFrom() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " adds one amount to the damage being settled; it has no \"times\" / \"times_from\" -- those "
                            + "count instances, and a count is not an amount (source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.addDamageFlat(ctx.effect(), ctx.firing());
    }
}
