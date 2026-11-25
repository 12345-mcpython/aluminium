package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code HEAL}: restore HP, credited to the <b>rule owner</b> as the healer.
 *
 * <p>The owner rather than {@code null} is what makes "healing provided by a teammate" readable, and what lets a
 * rule-driven heal read the owner's healing boost exactly as a skill's does. Registered here so the op has one dispatch
 * path; its load-time checks are still in {@code TriggerValidator}.
 */
final class HealOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.HEAL;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        for (CanHit target : ctx.targets()) {
            ctx.battle().heal(ctx.firing().owner(), target,
                    TriggerInterpreter.grantAmount(ctx.effect(), target, ctx.firing()));
        }
    }
}
