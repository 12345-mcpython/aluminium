package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code EXTEND_BUFF}: lengthen a state that is already on the unit.
 *
 * <p>The mirror of a duration and not of a grant: applying the state again would replace it, which is why "one more
 * turn" needs its own op. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator.validateOne}.
 */
final class ExtendBuffOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.EXTEND_BUFF;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.extendBuff(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
