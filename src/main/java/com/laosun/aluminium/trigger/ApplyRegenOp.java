package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code APPLY_REGEN}: a heal-over-time state, the healing twin of {@code APPLY_DOT}.
 *
 * <p>Its magnitude is a HEAL amount, so it takes the heal/shield scale vocabulary, not the {@code self_attr:} one the
 * modifier family uses - "equal to 20% of her Max HP" is {@code owner_max_hp}.
 */
final class ApplyRegenOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.APPLY_REGEN;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireAmountOrScale(effect, op, rule, TriggerValidator.SCALES, "Max HP");
        TriggerValidator.requireDuration(effect, op, rule);
        TriggerValidator.requireBuff(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.applyRegen(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
