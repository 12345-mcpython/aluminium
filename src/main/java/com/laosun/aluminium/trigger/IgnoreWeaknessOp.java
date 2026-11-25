package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code IGNORE_WEAKNESS}: the next N casts do not need a weakness to reduce toughness.
 *
 * <p>The cast still reports through; only the weakness gate stops refusing it, which is why the flag lives on the unit
 * and is read by {@code SkillExecutor} rather than here. Registered here so the op has one dispatch path; it has no
 * load-time checks today (it falls through to {@code TriggerValidator}'s default arm).
 */
final class IgnoreWeaknessOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.IGNORE_WEAKNESS;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        int casts = ctx.effect().getAmount() == null ? 1 : (int) Math.round(ctx.effect().getAmount());
        for (CanHit target : ctx.targets()) {
            target.markIgnoreWeaknessCasts(casts);
        }
    }
}
