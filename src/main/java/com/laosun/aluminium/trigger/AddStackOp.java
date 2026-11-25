package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code ADD_STACK}: add layers to a named counter.
 *
 * <p>A counter is a named stack buff, so it needs a name and a lifetime; {@code unbounded_stacks} and a ceiling are
 * refused together, because the document either gives one or it does not. {@code SET_STACKS} is the "make it exactly
 * N" twin.
 */
final class AddStackOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.ADD_STACK;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (Boolean.TRUE.equals(effect.getUnboundedStacks()) && effect.getMaxStacks() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " states BOTH \"unbounded_stacks\" and \"max_stacks\": the document either gives a "
                            + "ceiling or it does not (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireBuff(effect, op, rule);
        TriggerValidator.requireDuration(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.addStack(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
