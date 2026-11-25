package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code REGISTER_HEAL_BONUS}: a heal bonus whose applicability is decided per heal, not per unit.
 *
 * <p>"When I heal a target below 30% HP" cannot be an attribute (one stat on the healer would raise every heal) and
 * cannot be a fire-time rule (no pre-heal event exists), so the condition rides on the effect's {@code target_when} and
 * is asked by {@code Battle.calculateHeal}. Registered here so the op has one dispatch path.
 */
final class RegisterHealBonusOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REGISTER_HEAL_BONUS;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.registerHealBonus(ctx.effect(), ctx.firing());
    }
}
