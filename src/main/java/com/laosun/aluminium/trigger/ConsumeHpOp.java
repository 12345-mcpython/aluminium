package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.enums.TriggerEvent;

/**
 * {@code CONSUME_HP}: pay HP as a cost, and announce it as its own event.
 *
 * <p>Distinct from taking damage: {@code HP_CONSUMED} is the event for "paid", and the amount travels in the
 * <b>amount</b> slot (it used to be passed as a hit count, so every {@code event_amount} condition on this event read
 * zero). Registered here so the op has one dispatch path; its load-time checks are still in {@code TriggerValidator}.
 */
final class ConsumeHpOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.CONSUME_HP;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        for (CanHit spender : ctx.targets()) {
            if (spender == null) {
                continue;
            }
            double spent = spender.consumeHp(TriggerInterpreter.grantAmount(ctx.effect(), spender, ctx.firing()));
            if (spent > 0) {
                ctx.battle().fire(TriggerPayload.Simple.of(TriggerEvent.HP_CONSUMED, spender, spender, 0, spent));
            }
        }
    }
}
