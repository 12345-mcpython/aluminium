package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code MODIFY_DAMAGE_TAKEN}: the damage-taken zone - vulnerability when positive, reduction when negative.
 *
 * <p>Neither direction is an attribute, which is why {@code MODIFY_ATTR} cannot express it. A one-shot effect on the
 * instance being settled ({@code instance}) needs no lifetime; every persistent zone still requires one.
 */
final class ModifyDamageTakenOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.MODIFY_DAMAGE_TAKEN;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireScopeElement(effect, op, rule);
        TriggerValidator.requireBaseChance(effect, op, rule);
        TriggerValidator.requirePercent(effect, op, rule);
        TriggerValidator.requireNonZeroPercent(effect, op, rule);
        if (!Boolean.TRUE.equals(effect.getInstance())) {
            TriggerValidator.requireDuration(effect, op, rule);
        }
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.modifyDamageTaken(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
