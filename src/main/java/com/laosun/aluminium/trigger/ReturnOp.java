package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code RETURN}: bring a departed unit back - targetable again, and back in the action order.
 *
 * <p>The queue schedules it <b>one full cycle from now</b>, which is the engine's ordinary "joins mid-battle" rule
 * ({@code Queue.addCombatant}). The unit does not get its old slot back, and that is the honest reading of
 * 「回到原处」 for a system that only tracks remaining time.
 */
final class ReturnOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RETURN;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        TriggerValidator.requireNoDuration(effect, op().name(), rule);
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        for (CanHit arriving : ctx.targets()) {
            arriving.setUntargetable(false);
            ctx.battle().queue().addCombatant(arriving);
        }
    }
}
