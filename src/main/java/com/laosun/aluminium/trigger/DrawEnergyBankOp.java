package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code DRAW_ENERGY_BANK}: empty the banked overflow back into the energy bar.
 *
 * <p>A separate op from {@code GAIN_ENERGY} because the amount is not stated anywhere - it is whatever the unit
 * happened to bank, so stating {@code amount} or a {@code resource} here is refused. Nothing about the effect is read at
 * fire time beyond its target, which {@link OpContext#requireTarget} resolves.
 */
final class DrawEnergyBankOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DRAW_ENERGY_BANK;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        if (effect.getAmount() != null || effect.getResource() != null || effect.getValue() != null) {
            throw new IllegalArgumentException(
                    "Op " + op().name() + " restores what the energy bank holds, so it states neither \"amount\" nor "
                            + "\"resource\" (source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        ctx.requireTarget("target").drawEnergyBank();
    }
}
