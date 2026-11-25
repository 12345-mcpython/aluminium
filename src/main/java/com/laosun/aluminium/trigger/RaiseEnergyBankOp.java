package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code RAISE_ENERGY_BANK}: set how much energy may overflow into the bank.
 *
 * <p>About the <b>energy bar</b>, not a resource - resource overflow is {@code RAISE_RESOURCE_CAP}, and the two must
 * not share a spelling, so naming a {@code resource} here is refused. The number is the ALLOWANCE itself, not a delta:
 * both sentences state the total, and a derived delta (200 − 120 = 80) is a number no param column carries.
 */
final class RaiseEnergyBankOp implements OpHandler<RaiseEnergyBankArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_ENERGY_BANK;
    }

    @Override
    public RaiseEnergyBankArgs parse(EffectSpec effect) {
        return new RaiseEnergyBankArgs(effect);
    }

    @Override
    public void validate(RaiseEnergyBankArgs args, EffectSpec effect, TriggerSpec rule) {
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
    public void apply(OpContext ctx, RaiseEnergyBankArgs args) {
        CanHit holder = ctx.requireTarget("target");
        holder.setEnergyBankCap(args.magnitude().getValue() != null
                ? TriggerMagnitudes.valueOf(args.magnitude(), ctx.firing())
                : args.magnitude().getAmount());
    }
}
