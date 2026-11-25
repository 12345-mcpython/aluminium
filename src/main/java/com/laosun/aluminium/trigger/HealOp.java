package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code HEAL}: restore HP, credited to the <b>rule owner</b> as the healer.
 *
 * <p>The owner rather than {@code null} is what makes "healing provided by a teammate" readable, and what lets a
 * rule-driven heal read the owner's healing boost exactly as a skill's does. Healing has no duration - HP comes back
 * and stays back - so {@code turns} is refused rather than silently dropped.
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
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireAmountOrScale(effect, op, rule, TriggerValidator.SCALES, "Max HP");
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        TriggerValidator.requireNoDuration(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        for (CanHit target : ctx.targets()) {
            ctx.battle().heal(ctx.firing().owner(), target,
                    TriggerInterpreter.grantAmount(ctx.effect(), target, ctx.firing()));
        }
    }
}
