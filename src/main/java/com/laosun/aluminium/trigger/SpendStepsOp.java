package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code SPEND_STEPS}: consume {@code floor(value / step) * step} of a counter.
 *
 * <p>The paying half of {@code per_steps}, which counts the milestones an effect repeats without spending them; the two
 * are separate because one rule may pay two effects per milestone while the counter is charged once. A repeat count
 * here would spend the same milestones once per iteration, so {@code per_steps} is refused.
 */
final class SpendStepsOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SPEND_STEPS;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (effect.getSteps() == null || effect.getSteps().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"steps\" (\"<counter>:<step>\"), which says which counter is "
                            + "consumed and how wide one milestone is (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireCounterSteps(effect.getSteps().trim(), op + " steps", rule);
        if (effect.getPerSteps() != null && !effect.getPerSteps().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"per_steps\" as well, but it consumes milestones rather than "
                            + "repeating an effect; its own spelling is \"steps\" (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.spendSteps(ctx.effect(), ctx.firing());
    }
}
