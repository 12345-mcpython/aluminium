package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.CanHit;

import java.util.List;

/**
 * {@code DAMAGE}: settle one damage instance per victim, {@code times} over.
 *
 * <p>{@code times} repeats the <b>whole</b> settlement and the target is re-drawn inside the loop, which is what
 * "randomly a single enemy, each time" means. {@code split} makes the stated magnitude the <b>total</b>, divided among
 * the victims that can really take it - an invulnerable victim is counted (its share is simply not settled) but a
 * corpse is not, because counting it would make part of the total vanish with nothing to see. Registered here so the
 * op has one dispatch path; its load-time checks are still in {@code TriggerValidator}.
 */
final class DamageOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DAMAGE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        EffectSpec effect = ctx.effect();
        int times = TriggerInterpreter.timesCount(ctx.battle(), effect, ctx.firing());
        if (times <= 0) {
            return;
        }
        for (int repeat = 0; repeat < times; repeat++) {
            List<CanHit> victims = ctx.targets();
            int divisor = 1;
            if (Boolean.TRUE.equals(effect.getSplit())) {
                divisor = 0;
                for (CanHit victim : victims) {
                    if (victim != null && !victim.isDeath()) {
                        divisor++;
                    }
                }
                divisor = Math.max(1, divisor);
            }
            for (CanHit victim : victims) {
                TriggerInterpreter.damage(ctx.battle(), effect, ctx.firing(), victim, divisor);
            }
        }
    }
}
