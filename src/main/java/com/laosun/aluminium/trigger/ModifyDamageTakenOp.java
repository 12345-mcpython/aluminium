package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code MODIFY_DAMAGE_TAKEN}: the damage-taken zone - vulnerability when positive, reduction when negative.
 *
 * <p>Neither direction is an attribute, which is why {@code MODIFY_ATTR} cannot express it. Registered here so the op
 * has one dispatch path; its load-time checks are still in {@code TriggerValidator}.
 */
final class ModifyDamageTakenOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.MODIFY_DAMAGE_TAKEN;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.modifyDamageTaken(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
