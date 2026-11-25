package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code RAISE_ENERGY_BANK}: set how much energy may overflow into the bank.
 *
 * <p>About the <b>energy bar</b>, not a resource - resource overflow is {@code RAISE_RESOURCE_CAP}, and the two must
 * not share a spelling, so naming a {@code resource} here is refused.
 */
final class RaiseEnergyBankOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_ENERGY_BANK;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requirePositiveAmount(effect, op, rule);
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        if (effect.getResource() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " sets the ENERGY bar's overflow allowance, so it takes no \"resource\" - "
                            + "resource overflow is RAISE_RESOURCE_CAP (source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.energyBankCap(ctx.effect(), ctx.firing());
    }
}
