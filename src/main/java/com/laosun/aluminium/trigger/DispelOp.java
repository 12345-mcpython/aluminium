package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code DISPEL}: remove up to {@code amount} negative effects, newest first.
 *
 * <p>Either an amount, or a <b>class</b> - and with a class the amount is optional, because "all control-class negative
 * states" counts nothing. Both together is legal too: "the newest two of that class". What counts as negative is
 * {@code AbstractBuff.isDebuff()}, decided per buff class.
 */
final class DispelOp implements OpHandler<DispelArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DISPEL;
    }

    @Override
    public DispelArgs parse(EffectSpec effect) {
        return new DispelArgs(effect.getKind(), effect);
    }

    @Override
    public void validate(DispelArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (args.kind() != null && !args.kind().isBlank()) {
            TriggerValidator.requireDebuffClass(effect, op, rule);
        } else {
            TriggerValidator.requirePositiveAmount(effect, op, rule);
        }
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, DispelArgs args) {
        TriggerEffects.dispel(ctx, args);
    }
}
